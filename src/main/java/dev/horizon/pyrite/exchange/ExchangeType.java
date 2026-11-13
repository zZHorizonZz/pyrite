package dev.horizon.pyrite.exchange;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.identity.Account;
import dev.horizon.pyrite.identity.AccountType;
import dev.horizon.pyrite.identity.Identifier;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import dev.horizon.pyrite.storage.DatabaseTime;
import dev.horizon.pyrite.storage.ExchangeBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The exchanges of the declaration: it starts an exchange of a declared definition, from an identifier, a subject or
 * both, and takes the answers to an exchange under way. Each start and each right answer moves the exchange past the
 * stages that its evidence meets, prepares the stage that it reaches, and writes it; past the last stage, the exchange
 * ends.
 *
 * <p>
 * An exchange that starts from an identifier that no account has does not fail; it takes the same answers as any
 * other, none of them right, so that a caller learns nothing of which accounts exist.
 */
public final class ExchangeType {

  static final String EXCHANGES = "exchanges/";

  private record Origin(Optional<Account> account, Optional<Identifier> identifier) {
  }

  private final ExchangeBackend backend;
  private final TypeSystem types;
  private final AuthenticationOptions options;
  private final AccountType accounts;
  private final ExchangeInterceptor interceptor;
  private final VerifierSet verifiers;
  private final ExchangeEnd end;

  public ExchangeType(Database database, TypeSystem types, AuthenticationOptions options, AccountType accounts,
      KeyType keys, ExchangeInterceptor interceptor) {
    this.backend = new ExchangeBackend(database);
    this.types = types;
    this.options = options;
    this.accounts = accounts;
    this.interceptor = interceptor;
    this.verifiers = new VerifierSet(accounts.credentials(), accounts.identifiers(), keys, options);
    this.end = new ExchangeEnd(interceptor, accounts);
  }

  /**
   * Starts an exchange of a declared definition, after the interceptors of the start, and gives the codes of its first
   * stage to the interceptors of codes, or in the response when there are none.
   *
   * @param request the definition, and the subject, the identifier, or both, that its input takes
   * @return the exchange, at its first stage, or done if it needs no answer; or a failure with {@code INVALID_ARGUMENT}
   * for a definition that is not declared, a missing identifier or a subject that is not an account, or with
   * {@code NOT_FOUND} for a subject without an account, or an identifier that is not of the subject's account
   */
  public Future<dev.horizon.pyrite.authentication.v1.Exchange> start(StartExchangeRequest request) {
    return definition(request.getDefinition()).compose(definition -> origin(definition, request).compose(origin -> {
      Tables.Exchange.Builder row = Tables.Exchange.newBuilder().setId(UUID.randomUUID().toString())
          .setDefinition(definition.getName()).setRevision(1).setProofs(Evidence.NOTHING.json().encode()).setState("{}");
      origin.account().map(Account::name).ifPresent(row::setAccount);
      origin.identifier().map(Identifier::name).ifPresent(row::setIdentifier);
      Exchange exchange = exchange(definition, row, true, false);
      exchange.expireAfter(options.getExchangeLifetime());
      return interceptor.start(exchange, origin.account()).compose(ignored -> first(exchange))
          .compose(step -> backend.start(exchange.row(), exchange.times()).compose(written -> finish(step, written)));
    }));
  }

  /**
   * Takes an answer to the current stage of an exchange. A request with the id of the last right answer gets its
   * response again, and does nothing more.
   *
   * @param request the exchange, its revision, the id of the request and the answer
   * @return the exchange after the answer, or a failure with {@code PERMISSION_DENIED} for a wrong answer, which counts
   * against the exchange without moving its revision; with {@code INVALID_ARGUMENT} for an answer that the stage does
   * not take or that does not parse, or a name that is not of an exchange; with {@code NOT_FOUND} for an exchange that
   * does not exist; with {@code ABORTED} for another revision; with {@code RESOURCE_EXHAUSTED} after the most wrong
   * answers that the options allow; or with {@code FAILED_PRECONDITION} when the exchange is done or expired
   */
  public Future<dev.horizon.pyrite.authentication.v1.Exchange> answer(ContinueExchangeRequest request) {
    return find(request.getExchange()).compose(exchange -> answer(exchange, request));
  }

  private Future<dev.horizon.pyrite.authentication.v1.Exchange> answer(Exchange exchange,
      ContinueExchangeRequest request) {
    Optional<dev.horizon.pyrite.authentication.v1.Exchange> replay = exchange.replay(request.getRequestId());
    if (replay.isPresent()) {
      return Future.succeededFuture(replay.get());
    }
    Optional<PyriteException> refusal = exchange.refusal(request.getRevision(), options.getMaxAnswers());
    if (refusal.isPresent()) {
      return fail(exchange, refusal.get());
    }
    Progress progress = exchange.progress();
    Optional<Verifier<?>> verifier = verifiers.at(progress.current(), progress.evidence()).stream()
        .filter(candidate -> candidate.answers(request.getAnswer())).findFirst();
    if (verifier.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The exchange " + exchange.name()
          + " takes no " + request.getAnswer().getTypeUrl() + " now."));
    }
    Tables.Exchange before = exchange.row();
    exchange.take(request.getRequestId());
    return verifier.get().accept(exchange, request.getAnswer()).compose(gain -> gain.isEmpty()
        ? backend.miss(before).compose(ignored -> fail(exchange, new PyriteException(GrpcStatus.PERMISSION_DENIED,
            "The answer is not right.")))
        : next(exchange, gain.get()).compose(step -> backend.save(exchange.row(), before.getRevision(), exchange
            .times()).compose(written -> finish(step, written)).compose(message -> backend
                .remember(exchange.row(),
                    message.toByteString())
                .map(message))));
  }

  /**
   * Gives the step of an exchange that starts: it moves past the stages that need no answer, and prepares the stage
   * that it reaches. No stage ends, so the interceptors of stage ends do not run.
   */
  private Future<Step> first(Exchange exchange) {
    exchange.pass(Evidence.NOTHING);
    return reach(exchange, Future.succeededFuture(), true);
  }

  /**
   * Gives the step after a right answer. An answer that meets the stage moves the exchange past every stage that the
   * evidence meets, runs the interceptors of stage ends once, and prepares the stage that it reaches, such as with a
   * new code. An answer that does not meet it yet keeps the challenges of the stage.
   */
  private Future<Step> next(Exchange exchange, Evidence gain) {
    if (!exchange.pass(gain)) {
      return reach(exchange, Future.succeededFuture(), false);
    }
    return reach(exchange, exchange.claimant().account().compose(account -> interceptor.stageEnd(exchange, account)),
        true);
  }

  /**
   * Builds the response at the stage that the exchange is at, after the interceptors of stage ends; past the last
   * stage, it ends the exchange.
   *
   * @param prepare whether the stage is new to the exchange, so that it gets a new nonce and its verifiers prepare,
   * such as by making a code
   */
  private Future<Step> reach(Exchange exchange, Future<Void> stageEnd, boolean prepare) {
    dev.horizon.pyrite.authentication.v1.Exchange.Builder message = dev.horizon.pyrite.authentication.v1.Exchange
        .newBuilder().setName(exchange.name()).setDefinition(exchange.definition().getName())
        .setRevision(exchange.revision());
    exchange.providerAccount().ifPresent(message::setProviderAccount);
    Progress progress = exchange.progress();
    if (progress.isDone()) {
      return stageEnd.compose(ignored -> end.run(exchange)).map(result -> new Step(message.setResult(result),
          List.of()));
    }
    List<Verifier<?>> taken = verifiers.at(progress.current(), progress.evidence());
    List<Delivery> deliveries = new ArrayList<>();
    Future<Void> preparing = stageEnd;
    if (prepare) {
      exchange.renewNonce(options.getCodeLifetime());
      for (Verifier<?> verifier : taken) {
        preparing = preparing.compose(ignored -> verifier.prepare(exchange).map(delivery -> {
          delivery.ifPresent(deliveries::add);
          return null;
        }));
      }
    }
    return preparing.compose(ignored -> {
      List<Future<Challenge>> challenges = taken.stream().map(verifier -> verifier.challenge(exchange))
          .toList();
      return Future.all(challenges).map(all -> new Step(message.addAllChallenges(challenges.stream()
          .map(Future::result).toList()), deliveries));
    });
  }

  /**
   * Finishes the response from the exchange as the database wrote it: with the time that the exchange expires, and
   * the codes that no interceptor delivered, with the time that they expire.
   */
  private Future<dev.horizon.pyrite.authentication.v1.Exchange> finish(Step step, Tables.Exchange written) {
    step.message().setExpireTime(DatabaseTime.timestamp(written.getExpireTime()));
    Future<Void> delivering = Future.succeededFuture();
    for (Delivery code : step.deliveries()) {
      Delivery delivery = code.toBuilder().setExpireTime(DatabaseTime.timestamp(written.getNonceExpireTime())).build();
      delivering = delivering.compose(ignored -> interceptor.deliver(delivery).map(taken -> {
        if (!taken) {
          step.message().addDeliveries(delivery);
        }
        return null;
      }));
    }
    return delivering.map(ignored -> step.message().build());
  }

  /**
   * Fails the exchange, after the interceptors of failures; one of them can fail it with another cause.
   */
  private <T> Future<T> fail(Exchange exchange, PyriteException cause) {
    return exchange.claimant().account().compose(account -> interceptor.failure(exchange, account))
        .compose(ignored -> Future.failedFuture(cause));
  }

  /**
   * Finds an exchange of a name, expired or not; an expired exchange refuses every answer.
   *
   * @param name the name, such as {@code exchanges/9f1c2a64-7d1e-4f0a-9a43-1f6c0e2b8d55}
   * @return the exchange, or a failure with {@code INVALID_ARGUMENT} for a name that is not of the form
   * {@code exchanges/{exchange}}, or with {@code NOT_FOUND}
   */
  private Future<Exchange> find(String name) {
    if (!name.startsWith(EXCHANGES)) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The exchange " + name + " is not a"
          + " name of the form exchanges/{exchange}."));
    }
    String id = name.substring(EXCHANGES.length());
    return backend.findLive(id).compose(live -> live.isPresent() ? exchange(live.get(), true)
        : backend.findById(id).compose(expired -> expired.isEmpty()
            ? Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The exchange " + name
                + " does not exist."))
            : exchange(expired.get(), false)));
  }

  /**
   * Makes an exchange of a row that the database holds, with whether its nonce has expired on the clock of the
   * database.
   */
  private Future<Exchange> exchange(Tables.Exchange row, boolean live) {
    Future<Boolean> nonceLive = row.hasNonce() ? backend.hasLiveNonce(row.getId()) : Future.succeededFuture(false);
    return definition(row.getDefinition()).compose(definition -> nonceLive.map(fresh -> exchange(definition,
        row.toBuilder(), live, fresh)));
  }

  private Exchange exchange(ExchangeDefinition definition, Tables.Exchange.Builder row, boolean live,
      boolean nonceLive) {
    Claimant claimant = new Claimant(EXCHANGES + row.getId(), definition.getInput(),
        row.hasAccount() ? Optional.of(row.getAccount()) : Optional.empty(),
        row.hasIdentifier() ? Optional.of(row.getIdentifier()) : Optional.empty(), accounts);
    return new Exchange(definition, row, new HashMap<>(), live, nonceLive, claimant);
  }

  private Future<ExchangeDefinition> definition(String name) {
    return types.exchange(name).map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new PyriteException(
        GrpcStatus.INVALID_ARGUMENT, "The exchange " + name + " is not declared.")));
  }

  private Future<Origin> origin(ExchangeDefinition definition, StartExchangeRequest request) {
    return switch (definition.getInput()) {
      case IDENTIFIER -> identifier(request).compose(identifier -> identifier.isEmpty()
          ? Future.succeededFuture(new Origin(Optional.empty(), Optional.empty()))
          : accounts.account(identifier.get().account()).map(account -> new Origin(Optional.of(account), identifier))
              .recover(failure -> failure instanceof PyriteException refusal
                  && refusal.status() == GrpcStatus.NOT_FOUND
                      ? Future.succeededFuture(new Origin(Optional.empty(), identifier))
                      : Future.failedFuture(failure)));
      case SUBJECT -> accounts.account(Subject.parse(request.getSubject()))
          .map(account -> new Origin(Optional.of(account), Optional.empty()));
      case SUBJECT_IDENTIFIER -> accounts.account(Subject.parse(request.getSubject()))
          .compose(account -> identifier(request).compose(identifier -> identifier.isEmpty()
              || !identifier.get().account().equals(account.name())
                  ? Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The account " + account.name()
                      + " has no " + request.getIdentifierKind() + " " + request.getIdentifier() + "."))
                  : Future.succeededFuture(new Origin(Optional.of(account), identifier))));
      case NONE -> Future.succeededFuture(new Origin(Optional.empty(), Optional.empty()));
      case INPUT_UNSPECIFIED, UNRECOGNIZED -> Future.failedFuture(new IllegalStateException("The exchange "
          + definition.getName() + " has no input, which its declaration was checked for."));
    };
  }

  private Future<Optional<Identifier>> identifier(StartExchangeRequest request) {
    if (request.getIdentifier().isBlank()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The exchange "
          + request.getDefinition() + " starts from an identifier, and the request names none."));
    }
    return accounts.identifiers().find(request.getIdentifierKind(), request.getIdentifier());
  }
}
