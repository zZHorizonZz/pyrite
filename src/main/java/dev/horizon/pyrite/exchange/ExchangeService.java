package dev.horizon.pyrite.exchange;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.ContinueExchangeRequest;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.identity.AccountService;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.IdentifierService;
import dev.horizon.pyrite.key.KeyService;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import dev.horizon.pyrite.session.SessionService;
import dev.horizon.pyrite.storage.DatabaseTime;
import dev.horizon.pyrite.storage.ExchangeBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public final class ExchangeService {

  static final String EXCHANGES = "exchanges/";

  private record Origin(Optional<Tables.Account> account, Optional<Tables.Identifier> identifier) {
  }

  private final ExchangeBackend backend;
  private final TypeSystem types;
  private final AuthenticationOptions options;
  private final AccountService accounts;
  private final IdentifierService identifiers;
  private final FactorService factors;
  private final KeyService keys;
  private final ExchangeInterceptor interceptor;
  private final VerifierSet verifiers;
  private final ExchangeEnd end;

  public ExchangeService(Database database, TypeSystem types, AuthenticationOptions options, AccountService accounts,
      IdentifierService identifiers, FactorService factors, SessionService sessions, KeyService keys,
      ExchangeInterceptor interceptor) {
    this.backend = new ExchangeBackend(database);
    this.types = types;
    this.options = options;
    this.accounts = accounts;
    this.identifiers = identifiers;
    this.factors = factors;
    this.keys = keys;
    this.interceptor = interceptor;
    this.verifiers = new VerifierSet(factors, identifiers, keys, options);
    this.end = new ExchangeEnd(interceptor, accounts, identifiers, factors, sessions);
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
  public Future<Exchange> start(StartExchangeRequest request) {
    return definition(request.getDefinition()).compose(definition -> origin(definition, request).compose(origin -> {
      Tables.Exchange.Builder row = Tables.Exchange.newBuilder().setId(UUID.randomUUID().toString())
          .setDefinition(definition.getName()).setRevision(1).setProofs(Evidence.NOTHING.json().encode())
          .setState("{}");
      origin.account().map(Tables.Account::getName).ifPresent(row::setAccount);
      origin.identifier().map(Tables.Identifier::getName).ifPresent(row::setIdentifier);
      ExchangeRecord exchange = exchange(definition, row, true, false);
      exchange.expireAfter(options.getExchangeLifetime());
      return interceptor.start(exchange, origin.account()).compose(ignored -> first(exchange))
          .compose(step -> backend.start(exchange.row(), exchange.times())
              .compose(stored -> complete(exchange, step).compose(ignored -> finish(step, stored))));
    }));
  }

  /**
   * Takes an answer to the current stage of an exchange. A request with the id of the last right answer gets its
   * response again, and does nothing more.
   *
   * <p>
   * The answer counts against the exchange before it is verified, in the statement that checks the count, so answers at
   * once cannot pass the most wrong answers; a right answer gives its count back. A right answer claims the next
   * revision before the exchange does what it completes with, so two answers at once cannot both complete it.
   *
   * @param request the exchange, its revision, the id of the request and the answer
   * @return the exchange after the answer, or a failure with {@code PERMISSION_DENIED} for a wrong answer; with
   * {@code INVALID_ARGUMENT} for an answer that the stage does not take or that does not parse, or a name that is not
   * of an exchange; with {@code NOT_FOUND} for an exchange that does not exist; with {@code ABORTED} for another
   * revision; with {@code RESOURCE_EXHAUSTED} after the most wrong answers that the options allow; or with
   * {@code FAILED_PRECONDITION} when the exchange is done or expired
   */
  public Future<Exchange> answer(ContinueExchangeRequest request) {
    return find(request.getExchange()).compose(exchange -> replay(exchange, request).compose(replay -> replay
        .map(Future::succeededFuture).orElseGet(() -> answer(exchange, request))));
  }

  /**
   * Deletes the rows of exchanges that expired, with the responses that they keep.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return backend.purge();
  }

  private Future<Exchange> answer(ExchangeRecord exchange, ContinueExchangeRequest request) {
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
    return backend.attempt(before, options.getMaxAnswers()).compose(counted -> counted
        ? verify(exchange, before, verifier.get(), request)
        : backend.findById(before.getId()).compose(current -> fail(exchange, current.isPresent()
            && current.get().getRevision() == before.getRevision()
                ? new PyriteException(GrpcStatus.RESOURCE_EXHAUSTED, "The exchange " + exchange.name() + " took "
                    + options.getMaxAnswers() + " answers; start another.")
                : new PyriteException(GrpcStatus.ABORTED, "The exchange " + exchange.name() + " changed since"
                    + " revision " + before.getRevision() + "."))));
  }

  /**
   * Verifies an answer that counted. A right answer saves the exchange at its next revision, giving its count back, and
   * then completes the exchange if it is done.
   */
  private Future<Exchange> verify(ExchangeRecord exchange, Tables.Exchange before, Verifier<?> verifier,
      ContinueExchangeRequest request) {
    exchange.take(request.getRequestId());
    return verifier.accept(exchange, request.getAnswer()).compose(gain -> {
      if (gain.isEmpty()) {
        return fail(exchange, new PyriteException(GrpcStatus.PERMISSION_DENIED, "The answer is not right."));
      }
      return next(exchange, gain.get()).compose(step -> {
        Map<String, Expression> values = new HashMap<>(exchange.times());
        values.put("answers", Expression.subtract(Expression.column("answers"), Expression.value(1)));
        return backend.save(exchange.row(), before.getRevision(), values)
            .compose(stored -> complete(exchange, step).compose(ignored -> finish(step, stored)))
            .compose(message -> response(exchange, message).compose(sealed -> backend.remember(exchange.row(),
                sealed)).map(message));
      });
    });
  }

  /**
   * Gives the response to the last right answer again, for a request with its id at the revision before the exchange's,
   * once the response was kept.
   */
  private Future<Optional<Exchange>> replay(ExchangeRecord exchange, ContinueExchangeRequest request) {
    Optional<ByteString> sealed = exchange.response(request.getRequestId(), request.getRevision());
    if (sealed.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return keys.decrypt(options.getEncryptionKey(), sealed.get().toByteArray(), exchange.name().getBytes(
        StandardCharsets.UTF_8)).map(plain -> {
          try {
            return Optional.of(Exchange.parseFrom(plain.getPlaintext()));
          } catch (InvalidProtocolBufferException unreadable) {
            throw new IllegalStateException("The exchange " + exchange.name() + " holds a response that is not an"
                + " exchange", unreadable);
          }
        });
  }

  /**
   * Encrypts a response for a retry, with the name of the exchange as the associated data, so that the table holds no
   * token, code or recovery code that it gives out.
   */
  private Future<ByteString> response(ExchangeRecord exchange, Exchange message) {
    return keys.encrypt(options.getEncryptionKey(), message.toByteArray(), exchange.name().getBytes(
        StandardCharsets.UTF_8)).map(EncryptResponse::getCiphertext);
  }

  /**
   * Does what the exchange completes with, once its revision is claimed and it is done, and puts the result in the
   * response.
   */
  private Future<Void> complete(ExchangeRecord exchange, Step step) {
    if (!exchange.progress().isDone()) {
      return Future.succeededFuture();
    }
    return end.run(exchange).map(result -> {
      step.message().setResult(result);
      return null;
    });
  }

  /**
   * Gives the step of an exchange that starts: it moves past the stages that need no answer, and prepares the stage
   * that it reaches. No stage ends, so the interceptors of stage ends do not run.
   */
  private Future<Step> first(ExchangeRecord exchange) {
    exchange.pass(Evidence.NOTHING);
    return reach(exchange, Future.succeededFuture(), true);
  }

  /**
   * Gives the step after a right answer. An answer that meets the stage moves the exchange past every stage that the
   * evidence meets, runs the interceptors of stage ends once, and prepares the stage that it reaches, such as with a
   * new code. An answer that does not meet it yet keeps the challenges of the stage.
   */
  private Future<Step> next(ExchangeRecord exchange, Evidence gain) {
    if (!exchange.pass(gain)) {
      return reach(exchange, Future.succeededFuture(), false);
    }
    return reach(exchange, exchange.claimant().account().compose(account -> interceptor.stageEnd(exchange, account)),
        true);
  }

  /**
   * Builds the response at the stage that the exchange is at, after the interceptors of stage ends. Past the last
   * stage, the response has no challenges; the result comes once the exchange is saved.
   *
   * @param prepare whether the stage is new to the exchange, so that it gets a new nonce and its verifiers prepare,
   * such as by making a code
   */
  private Future<Step> reach(ExchangeRecord exchange, Future<Void> stageEnd, boolean prepare) {
    Exchange.Builder message = Exchange.newBuilder().setName(exchange.name())
        .setDefinition(exchange.definition().getName()).setRevision(exchange.revision());
    exchange.providerAccount().ifPresent(message::setProviderAccount);
    Progress progress = exchange.progress();
    if (progress.isDone()) {
      return stageEnd.map(ignored -> new Step(message, List.of()));
    }
    List<Verifier<?>> stageVerifiers = verifiers.at(progress.current(), progress.evidence());
    List<Delivery> deliveries = new ArrayList<>();
    Future<Void> preparing = stageEnd;
    if (prepare) {
      exchange.renewNonce(options.getCodeLifetime());
      for (Verifier<?> verifier : stageVerifiers) {
        preparing = preparing.compose(ignored -> verifier.prepare(exchange).map(delivery -> {
          delivery.ifPresent(deliveries::add);
          return null;
        }));
      }
    }
    return preparing.compose(ignored -> {
      List<Future<Challenge>> challenges = stageVerifiers.stream().map(verifier -> verifier.challenge(exchange))
          .toList();
      return Future.all(challenges).map(all -> new Step(message.addAllChallenges(challenges.stream()
          .map(Future::result).toList()), deliveries));
    });
  }

  /**
   * Finishes the response from the exchange as the database holds it: with the time that the exchange expires, and the
   * codes that no interceptor delivered, with the time that they expire.
   */
  private Future<Exchange> finish(Step step, Tables.Exchange stored) {
    step.message().setExpireTime(DatabaseTime.timestamp(stored.getExpireTime()));
    Future<Void> delivering = Future.succeededFuture();
    for (Delivery code : step.deliveries()) {
      Delivery delivery = code.toBuilder().setExpireTime(DatabaseTime.timestamp(stored.getNonceExpireTime())).build();
      delivering = delivering.compose(ignored -> interceptor.deliver(delivery).map(delivered -> {
        if (!delivered) {
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
  private <T> Future<T> fail(ExchangeRecord exchange, PyriteException cause) {
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
  private Future<ExchangeRecord> find(String name) {
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
  private Future<ExchangeRecord> exchange(Tables.Exchange row, boolean live) {
    Future<Boolean> nonceLive = row.hasNonce() ? backend.hasLiveNonce(row.getId()) : Future.succeededFuture(false);
    return definition(row.getDefinition()).compose(definition -> nonceLive.map(fresh -> exchange(definition,
        row.toBuilder(), live, fresh)));
  }

  private ExchangeRecord exchange(ExchangeDefinition definition, Tables.Exchange.Builder row, boolean live,
      boolean nonceLive) {
    Claimant claimant = new Claimant(EXCHANGES + row.getId(), definition.getInput(),
        row.hasAccount() ? Optional.of(row.getAccount()) : Optional.empty(),
        row.hasIdentifier() ? Optional.of(row.getIdentifier()) : Optional.empty(), accounts, identifiers, factors);
    return new ExchangeRecord(definition, row, new HashMap<>(), live, nonceLive, claimant);
  }

  private Future<ExchangeDefinition> definition(String name) {
    return types.exchange(name).map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new PyriteException(
        GrpcStatus.INVALID_ARGUMENT, "The exchange " + name + " is not declared.")));
  }

  private Future<Origin> origin(ExchangeDefinition definition, StartExchangeRequest request) {
    return switch (definition.getInput()) {
      case IDENTIFIER -> identifier(request).compose(identifier -> identifier.isEmpty()
          ? Future.succeededFuture(new Origin(Optional.empty(), Optional.empty()))
          : accounts.account(identifier.get().getParent())
              .map(account -> new Origin(Optional.of(account), identifier))
              .recover(failure -> failure instanceof PyriteException refusal
                  && refusal.status() == GrpcStatus.NOT_FOUND
                      ? Future.succeededFuture(new Origin(Optional.empty(), identifier))
                      : Future.failedFuture(failure)));
      case SUBJECT -> accounts.account(Subject.parse(request.getSubject()))
          .map(account -> new Origin(Optional.of(account), Optional.empty()));
      case SUBJECT_IDENTIFIER -> accounts.account(Subject.parse(request.getSubject()))
          .compose(account -> identifier(request, account.getName()).compose(identifier -> identifier.isEmpty()
              ? Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The account " + account.getName()
                  + " has no " + request.getIdentifierKind() + " " + request.getIdentifier() + "."))
              : Future.succeededFuture(new Origin(Optional.of(account), identifier))));
      case NONE -> Future.succeededFuture(new Origin(Optional.empty(), Optional.empty()));
      case INPUT_UNSPECIFIED, UNRECOGNIZED -> Future.failedFuture(new IllegalStateException("The exchange "
          + definition.getName() + " has no input, which its declaration was checked for."));
    };
  }

  private Future<Optional<Tables.Identifier>> identifier(StartExchangeRequest request) {
    if (request.getIdentifier().isBlank()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The exchange "
          + request.getDefinition() + " starts from an identifier, and the request names none."));
    }
    return identifiers.find(request.getIdentifierKind(), request.getIdentifier());
  }

  /**
   * Finds the identifier of a request that an account holds, verified or not, for an exchange of the account.
   */
  private Future<Optional<Tables.Identifier>> identifier(StartExchangeRequest request, String account) {
    if (request.getIdentifier().isBlank()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The exchange "
          + request.getDefinition() + " starts from an identifier, and the request names none."));
    }
    return identifiers.find(account, request.getIdentifierKind(), request.getIdentifier());
  }
}
