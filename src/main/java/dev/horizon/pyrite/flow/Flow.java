package dev.horizon.pyrite.flow;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import dev.horizon.pyrite.identity.Credential;
import dev.horizon.pyrite.identity.Identifier;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import dev.horizon.pyrite.storage.DatabaseTime;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A flow under way: the stage of its definition that it is at, the evidence that its answers gave, and the account
 * and identifier that it is for. Each answer moves it on, and the last one completes it.
 *
 * <p>
 * A right answer moves the flow to its next revision; a wrong one counts against it and leaves it at its revision. Two
 * answers to the same revision cannot both win: the database stores a flow only over the revision that it read. The
 * database decides when a flow and its code expire.
 */
public final class Flow {

  private static final int CHALLENGE_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final FlowType type;
  private final FlowDefinition definition;
  private final Tables.Flow.Builder row;
  private final boolean live;
  private final Map<String, Expression> set = new HashMap<>();

  /**
   * Creates a flow of a row.
   *
   * @param live whether the flow has not expired, on the clock of the database
   */
  Flow(FlowType type, FlowDefinition definition, Tables.Flow.Builder row, boolean live) {
    this.type = type;
    this.definition = definition;
    this.row = row;
    this.live = live;
  }

  /**
   * What an answer, or the start, gives besides the row of the flow: the response, which still needs the times
   * that the database writes, and the codes to deliver.
   */
  private record Step(dev.horizon.pyrite.authentication.v1.Flow.Builder message, List<Delivery> deliveries) {
  }

  /**
   * Takes an answer to the current stage. A request with the id of the last right answer gets its response again,
   * and does nothing more.
   *
   * @param request the flow, its revision, the id of the request and the answer
   * @return the flow after the answer, or a failure with {@code PERMISSION_DENIED} for a wrong answer, which counts
   * against the flow without moving its revision; with {@code INVALID_ARGUMENT} for an answer that the stage does not
   * take or that does not parse; with {@code ABORTED} for another revision; with {@code RESOURCE_EXHAUSTED} after the
   * most wrong answers that the options allow; or with {@code FAILED_PRECONDITION} when the flow is done or expired
   */
  public Future<dev.horizon.pyrite.authentication.v1.Flow> answer(ContinueFlowRequest request) {
    if (!request.getRequestId().isEmpty() && request.getRequestId().equals(row.getRequestId())) {
      return Future.succeededFuture(replay());
    }
    Optional<PyriteException> refusal = refusal(request);
    if (refusal.isPresent()) {
      return fail(refusal.get());
    }
    Evidence evidence = Evidence.of(new JsonObject(row.getProofs()));
    Optional<Authentication<?>> authentication = requirement(row.getStage()).authentications(evidence).stream()
        .filter(candidate -> candidate.answers(request.getAnswer())).findFirst();
    if (authentication.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The flow " + name() + " takes no "
          + request.getAnswer().getTypeUrl() + " now."));
    }
    Tables.Flow before = row.build();
    row.setRevision(before.getRevision() + 1);
    return authentication.get().accept(this, request.getAnswer()).compose(gain -> {
      if (gain.isEmpty()) {
        return type.backend().miss(before).compose(ignored -> fail(new PyriteException(GrpcStatus.PERMISSION_DENIED,
            "The answer is not right.")));
      }
      return advance(evidence.and(gain.get()), false).compose(step -> type.backend()
          .save(row.setRequestId(request.getRequestId()).build(), before.getRevision(), set)
          .compose(written -> finish(step, written))
          .compose(message -> type.backend().remember(row.build(), message.toByteString()).map(message)));
    });
  }

  FlowType type() {
    return type;
  }

  String id() {
    return row.getId();
  }

  String accountName() {
    return row.getAccount();
  }

  Future<Optional<Account>> account() {
    if (!row.hasAccount()) {
      return Future.succeededFuture(Optional.empty());
    }
    return type.accounts().account(row.getAccount()).map(Optional::of)
        .recover(failure -> failure instanceof PyriteException refusal && refusal.status() == GrpcStatus.NOT_FOUND
            ? Future.succeededFuture(Optional.empty())
            : Future.failedFuture(failure));
  }

  /**
   * Gives the active credentials of a kind of the account of the flow.
   */
  <C extends Credential> Future<List<C>> credentials(Class<C> kind) {
    return account().compose(account -> account.isEmpty() ? Future.succeededFuture(List.<Credential> of())
        : account.get().credentials()).map(credentials -> credentials.stream().filter(kind::isInstance)
            .map(kind::cast).filter(Credential::isActive).toList());
  }

  /**
   * Gives the account of the flow, or a failure with {@code FAILED_PRECONDITION} when it names none, or the account
   * was deleted while the flow went on.
   */
  Future<Account> requireAccount() {
    return account().compose(found -> found.isPresent() ? Future.succeededFuture(found.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The flow " + name() + " has no"
            + " account: it names none, or the account was deleted.")));
  }

  /**
   * Gives the identifier of the flow, or a failure with {@code FAILED_PRECONDITION} when it names none, or the
   * identifier was removed while the flow went on.
   */
  Future<Identifier> requireIdentifier() {
    return identifier().compose(found -> found.isPresent() ? Future.succeededFuture(found.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The flow " + name() + " has no"
            + " identifier: it names none, or the identifier was removed.")));
  }

  Future<Optional<Identifier>> identifier() {
    if (!row.hasIdentifier()) {
      return Future.succeededFuture(Optional.empty());
    }
    return type.identifiers().identifier(row.getIdentifier());
  }

  /**
   * Gives the identifier that a code goes to: that of the flow, if it receives codes and is verified, or the flow
   * starts from a subject and an identifier of its account.
   */
  Future<Optional<Identifier>> recipient() {
    if (!row.hasAccount()) {
      return Future.succeededFuture(Optional.empty());
    }
    return identifier().map(found -> found.filter(identifier -> identifier.isDeliverable()
        && (identifier.isVerified() || definition.getInput() == FlowDefinition.Input.SUBJECT_IDENTIFIER)));
  }

  byte[] challenge() {
    return row.getChallenge().toByteArray();
  }

  void newChallenge() {
    byte[] challenge = new byte[CHALLENGE_BYTES];
    RANDOM.nextBytes(challenge);
    row.setChallenge(ByteString.copyFrom(challenge));
  }

  /**
   * Gives the MAC of the code of the flow, if it has one that has not expired.
   */
  Future<Optional<byte[]>> code() {
    if (!row.hasCodeMac()) {
      return Future.succeededFuture(Optional.empty());
    }
    return type.backend().hasLiveCode(row.getId()).map(live -> live ? Optional.of(row.getCodeMac().toByteArray())
        : Optional.empty());
  }

  /**
   * Gives the flow a code, which expires after the lifetime of codes from when the flow is written.
   */
  void code(byte[] mac) {
    row.setCodeMac(ByteString.copyFrom(mac)).clearCodeExpireTime();
    set.put("code_expire_time", add(now(), type.options().getCodeLifetime()));
  }

  void clearCode() {
    row.clearCodeMac().clearCodeExpireTime();
    set.remove("code_expire_time");
  }

  void enrol(Credential credential) {
    row.setFactor(credential.name());
  }

  boolean hasEnrolment() {
    return row.hasFactor();
  }

  <C extends Credential> Future<C> enrolment(Class<C> kind) {
    return type.credentials().credential(row.getFactor()).map(kind::cast);
  }

  /**
   * Starts the flow: it moves past what needs no answer, and is written with the time that it expires.
   */
  Future<dev.horizon.pyrite.authentication.v1.Flow> enter() {
    set.put("expire_time", add(now(), type.options().getFlowLifetime()));
    return advance(Evidence.NOTHING, true).compose(step -> type.backend().start(row.build(), set)
        .compose(written -> finish(step, written)));
  }

  String name() {
    return FlowType.FLOWS + row.getId();
  }

  String definition() {
    return definition.getName();
  }

  /**
   * Does what the declaration of the flow completes with, in its order, and fills in the result.
   */
  Future<Void> complete(Optional<Account> account, Result.Builder result) {
    if (account.isEmpty() && !definition.getCompletesList().isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The flow " + name()
          + " completes for an account, but has none: the account was deleted while the flow went on."));
    }
    Future<Void> applying = Future.succeededFuture();
    for (FlowDefinition.Completion completion : definition.getCompletesList()) {
      applying = applying.compose(ignored -> Effect.of(completion).apply(this, account.get(), result));
    }
    return applying;
  }

  private Requirement requirement(int stage) {
    return new Requirement(definition.getStagesList().get(stage));
  }

  private Optional<PyriteException> refusal(ContinueFlowRequest request) {
    if (row.getDone()) {
      return Optional.of(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The flow " + name() + " is done."));
    }
    if (!live) {
      return Optional.of(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The flow " + name() + " expired."));
    }
    if (row.getRevision() != request.getRevision()) {
      return Optional.of(new PyriteException(GrpcStatus.ABORTED, "The flow " + name() + " is at revision "
          + row.getRevision() + ", not " + request.getRevision() + "."));
    }
    if (row.getAnswers() >= type.options().getMaxAnswers()) {
      return Optional.of(new PyriteException(GrpcStatus.RESOURCE_EXHAUSTED, "The flow " + name() + " took "
          + type.options().getMaxAnswers() + " answers; start another."));
    }
    return Optional.empty();
  }

  /**
   * Moves the flow past every stage that its evidence meets. At a stage it reaches, it prepares the authentications
   * of the stage, such as a code to deliver; past the last stage, it completes.
   */
  private Future<Step> advance(Evidence evidence, boolean entering) {
    int stage = row.getStage();
    boolean moving = entering;
    while (stage < definition.getStagesList().size() && evidence.meets(requirement(stage))) {
      stage++;
      moving = true;
    }
    row.setStage(stage).setProofs(evidence.json().encode());
    dev.horizon.pyrite.authentication.v1.Flow.Builder message = dev.horizon.pyrite.authentication.v1.Flow
        .newBuilder().setName(name()).setDefinition(definition.getName()).setRevision(row.getRevision());
    Future<Void> stageEnd = moving && !entering
        ? account().compose(account -> type.interceptor().stageEnd(this, account))
        : Future.succeededFuture();
    if (stage == definition.getStagesList().size()) {
      row.setDone(true);
      return stageEnd.compose(ignored -> end()).map(result -> new Step(message.setResult(result), List.of()));
    }
    List<Authentication<?>> authentications = requirement(stage).authentications(evidence);
    List<Delivery> deliveries = new ArrayList<>();
    Future<Void> preparing = stageEnd;
    if (moving) {
      for (Authentication<?> authentication : authentications) {
        preparing = preparing.compose(ignored -> authentication.prepare(this).map(delivery -> {
          delivery.ifPresent(deliveries::add);
          return null;
        }));
      }
    }
    return preparing.compose(ignored -> {
      List<Future<Challenge>> challenges = authentications.stream().map(authentication -> authentication
          .challenge(this)).toList();
      return Future.all(challenges).map(all -> new Step(message.addAllChallenges(challenges.stream()
          .map(Future::result).toList()), deliveries));
    });
  }

  /**
   * Finishes the response to a step from the flow as it was written: with the times that the database wrote, and
   * the codes that no interceptor delivered.
   */
  private Future<dev.horizon.pyrite.authentication.v1.Flow> finish(Step step, Tables.Flow written) {
    step.message().setExpireTime(DatabaseTime.timestamp(written.getExpireTime()));
    Future<Void> delivering = Future.succeededFuture();
    for (Delivery code : step.deliveries()) {
      Delivery delivery = code.toBuilder().setExpireTime(DatabaseTime.timestamp(written.getCodeExpireTime())).build();
      delivering = delivering.compose(ignored -> type.interceptor().deliver(delivery).map(taken -> {
        if (!taken) {
          step.message().addDeliveries(delivery);
        }
        return null;
      }));
    }
    return delivering.map(ignored -> step.message().build());
  }

  /**
   * Ends the flow through the interceptors of its end, which start with the {@code CompletionHandler}.
   */
  private Future<Result> end() {
    Result.Builder result = Result.newBuilder();
    return account().compose(account -> {
      account.ifPresent(found -> result.setAccount(found.name()));
      return type.interceptor().end(this, account, result);
    }).map(ignored -> result.build());
  }

  /**
   * Fails the flow, after the interceptors of failures; one of them can fail it with another cause.
   */
  private <T> Future<T> fail(PyriteException cause) {
    return account().compose(account -> type.interceptor().failure(this, account))
        .compose(ignored -> Future.failedFuture(cause));
  }

  private dev.horizon.pyrite.authentication.v1.Flow replay() {
    try {
      return dev.horizon.pyrite.authentication.v1.Flow.parseFrom(row.getResponse());
    } catch (InvalidProtocolBufferException unreadable) {
      throw new IllegalStateException("The flow " + row.getId() + " holds a response that is not a flow",
          unreadable);
    }
  }
}
