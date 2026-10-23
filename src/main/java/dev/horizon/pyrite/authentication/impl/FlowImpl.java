package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FlowImpl implements Flow {

  private static final int CHALLENGE_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final FlowTypeImpl type;
  private final FlowDefinition definition;
  private final Tables.Flow.Builder row;

  FlowImpl(FlowTypeImpl type, FlowDefinition definition, Tables.Flow.Builder row) {
    this.type = type;
    this.definition = definition;
    this.row = row;
  }

  @Override
  public Future<dev.horizon.pyrite.authentication.v1.Flow> answer(ContinueFlowRequest request) {
    if (!request.getRequestId().isEmpty() && request.getRequestId().equals(row.getRequestId())) {
      return Future.succeededFuture(replay());
    }
    String name = FlowTypeImpl.FLOWS + row.getId();
    if (row.getDone()) {
      throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The flow " + name + " is done.");
    }
    if (!type.schema().clock().instant().isBefore(Instant.parse(row.getExpireTime()))) {
      throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The flow " + name + " expired.");
    }
    if (row.getRevision() != request.getRevision()) {
      throw new PyriteException(GrpcStatus.ABORTED, "The flow " + name + " is at revision " + row.getRevision()
          + ", not " + request.getRevision() + ".");
    }
    if (row.getAnswers() >= type.options().maxAnswers()) {
      throw new PyriteException(GrpcStatus.RESOURCE_EXHAUSTED, "The flow " + name + " took "
          + type.options().maxAnswers() + " answers; start another.");
    }
    Evidence evidence = Evidence.of(new JsonObject(row.getProofs()));
    Method<?> method = new Requirement(definition.getStagesList().get(row.getStage())).methods(evidence).stream()
        .filter(candidate -> candidate.answers(request.getAnswer())).findFirst()
        .orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The flow " + name + " takes no "
            + request.getAnswer().getTypeUrl() + " now."));
    Tables.Flow before = row.build();
    row.setRevision(before.getRevision() + 1);
    return method.accept(this, request.getAnswer()).compose(gained -> {
      if (gained.isEmpty()) {
        return type.backend().miss(before).compose(ignored -> Future.failedFuture(new PyriteException(
            GrpcStatus.PERMISSION_DENIED, "The answer is not right.")));
      }
      return advance(evidence.and(gained.get()), false).compose(message -> type.backend()
          .save(row.setRequestId(request.getRequestId()).setResponse(message.toByteString()).build(),
              before.getRevision())
          .map(message));
    });
  }

  FlowTypeImpl type() {
    return type;
  }

  String id() {
    return row.getId();
  }

  String accountName() {
    return row.getAccount();
  }

  Future<Optional<AccountImpl>> account() {
    if (!row.hasAccount()) {
      return Future.succeededFuture(Optional.empty());
    }
    return type.schema().get(row.getAccount()).map(found -> found.filter(account -> !account.isDeleted())
        .map(AccountImpl.class::cast));
  }

  <F extends Factor> Future<List<F>> factors(Class<F> kind) {
    return account().compose(account -> account.isEmpty() ? Future.succeededFuture(List.<Factor> of())
        : account.get().factors()).map(found -> found.stream().filter(kind::isInstance).map(kind::cast)
            .filter(Factor::isActive).toList());
  }

  Future<Optional<Identifier>> identifier() {
    if (!row.hasIdentifier()) {
      return Future.succeededFuture(Optional.empty());
    }
    return type.schema().get(row.getIdentifier()).map(found -> found.map(Identifier.class::cast));
  }

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

  Optional<byte[]> code() {
    return !row.hasCodeMac() || !row.hasCodeExpireTime()
        || !type.schema().clock().instant().isBefore(Instant.parse(row.getCodeExpireTime())) ? Optional.empty()
            : Optional.of(row.getCodeMac().toByteArray());
  }

  void code(byte[] mac, Instant expires) {
    row.setCodeMac(ByteString.copyFrom(mac)).setCodeExpireTime(expires.toString());
  }

  void clearCode() {
    row.clearCodeMac().clearCodeExpireTime();
  }

  void enrol(Factor factor) {
    row.setFactor(factor.name());
  }

  boolean hasEnrolment() {
    return row.hasFactor();
  }

  <F extends Factor> Future<F> enrolment(Class<F> kind) {
    return type.schema().require(row.getFactor()).map(kind::cast);
  }

  Future<dev.horizon.pyrite.authentication.v1.Flow> enter() {
    return advance(Evidence.NOTHING, true).compose(message -> type.backend().insert(row.build()).map(message));
  }

  private Future<dev.horizon.pyrite.authentication.v1.Flow> advance(Evidence evidence, boolean entered) {
    int stage = row.getStage();
    boolean moved = entered;
    while (stage < definition.getStagesList().size()
        && evidence.meets(new Requirement(definition.getStagesList().get(stage)))) {
      stage++;
      moved = true;
    }
    row.setStage(stage).setProofs(evidence.json().encode());
    Instant expires = Instant.parse(row.getExpireTime());
    dev.horizon.pyrite.authentication.v1.Flow.Builder message = dev.horizon.pyrite.authentication.v1.Flow
        .newBuilder().setName(FlowTypeImpl.FLOWS + row.getId()).setDefinition(definition.getName())
        .setRevision(row.getRevision())
        .setExpireTime(Timestamp.newBuilder().setSeconds(expires.getEpochSecond()).setNanos(expires.getNano()));
    if (stage == definition.getStagesList().size()) {
      row.setDone(true);
      return complete().map(result -> message.setResult(result).build());
    }
    List<Method<?>> methods = new Requirement(definition.getStagesList().get(stage)).methods(evidence);
    List<Delivery> deliveries = new ArrayList<>();
    Future<Void> prepared = Future.succeededFuture();
    if (moved) {
      for (Method<?> method : methods) {
        prepared = prepared.compose(ignored -> method.prepare(this).map(delivery -> {
          delivery.ifPresent(deliveries::add);
          return null;
        }));
      }
    }
    return prepared.compose(ignored -> {
      List<Future<Challenge>> challenges = methods.stream().map(method -> method.challenge(this)).toList();
      return Future.all(challenges).map(done -> message.addAllDeliveries(deliveries)
          .addAllChallenges(challenges.stream().map(Future::result).toList()).build());
    });
  }

  private Future<Result> complete() {
    Result.Builder result = Result.newBuilder();
    return account().compose(account -> {
      Future<Void> applied = Future.succeededFuture();
      for (FlowDefinition.Completion completion : definition.getCompletesList()) {
        applied = applied.compose(ignored -> Effect.of(completion).apply(this, account.orElseThrow(), result));
      }
      return applied;
    }).map(ignored -> result.build());
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
