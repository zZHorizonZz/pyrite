package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.identity.AccountService;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.IdentifierService;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import dev.horizon.pyrite.session.SessionService;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;
import java.util.UUID;

/**
 * The end of an exchange, once its last stage is met: the interceptors of the end first, so that one of them can stop
 * the exchange before anything changes, then what the declaration of the exchange completes with, in its order. An
 * exchange of no account that completes with {@code CREATE_ACCOUNT} creates the account first, for the provider
 * account of its answer; an exchange that found an account by the email address of a provider account links the
 * provider account to it.
 */
final class ExchangeEnd {

  private final ExchangeInterceptor interceptor;
  private final AccountService accounts;
  private final IdentifierService identifiers;
  private final FactorService factors;
  private final SessionService sessions;

  ExchangeEnd(ExchangeInterceptor interceptor, AccountService accounts, IdentifierService identifiers,
      FactorService factors, SessionService sessions) {
    this.interceptor = interceptor;
    this.accounts = accounts;
    this.identifiers = identifiers;
    this.factors = factors;
    this.sessions = sessions;
  }

  /**
   * Ends an exchange.
   *
   * @return the result, with the account and what the completions issued, verified or activated; or a failure with
   * {@code FAILED_PRECONDITION} when the account was deleted while the exchange went on, or with the cause that an
   * interceptor of the end gave
   */
  Future<Result> run(ExchangeRecord exchange) {
    Result.Builder result = Result.newBuilder();
    return exchange.claimant().account().compose(account -> interceptor.end(exchange, account)
        .compose(ignored -> account.isEmpty() && exchange.definition().getCompletesList()
            .contains(ExchangeDefinition.Completion.CREATE_ACCOUNT) ? create(exchange, result).map(Optional::of)
                : Future.succeededFuture(account)))
        .compose(found -> {
          if (found.isEmpty()) {
            return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange "
                + exchange.name() + " completes for an account, but has none: the account was deleted while the"
                + " exchange went on."));
          }
          String account = found.get().getName();
          result.setAccount(account);
          Future<Void> completing = exchange.claimant().accountName().isEmpty() ? Future.succeededFuture()
              : link(exchange, account, result);
          for (ExchangeDefinition.Completion completion : exchange.definition().getCompletesList()) {
            completing = completing.compose(done -> complete(exchange, completion, account, result));
          }
          return completing.map(done -> result.build());
        });
  }

  /**
   * Links the provider account that the exchange found an account for by its email address, if it did.
   */
  private Future<Void> link(ExchangeRecord exchange, String account, Result.Builder result) {
    return exchange.providerAccount().map(provider -> factors.enrolProvider(account, provider)
        .compose(pending -> factors.activate(pending.getName())).<Void> map(active -> {
          result.addFactors(FactorService.message(active));
          return null;
        })).orElseGet(Future::succeededFuture);
  }

  /**
   * Creates an account for the provider account of the exchange, with a random id, and links the provider account to
   * it. When the link fails, such as for a provider account that another exchange linked meanwhile, the account goes
   * again, so that no account without a factor stays.
   */
  private Future<Tables.Account> create(ExchangeRecord exchange, Result.Builder result) {
    Optional<ProviderAccount> provider = exchange.providerAccount();
    if (provider.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange " + exchange.name()
          + " creates an account, but no answer named a provider account."));
    }
    String id = "u" + UUID.randomUUID().toString().replace("-", "");
    return accounts.create(new Subject(TypeSystem.USER, id), id,
        dev.horizon.pyrite.authentication.v1.Account.Kind.HUMAN)
        .compose(account -> factors.enrolProvider(account.getName(), provider.get())
            .compose(pending -> factors.activate(pending.getName())).map(active -> {
              result.addFactors(FactorService.message(active));
              return account;
            })
            .recover(failure -> accounts.delete(account.getName()).transform(ignored -> Future.failedFuture(failure))));
  }

  private Future<Void> complete(ExchangeRecord exchange, ExchangeDefinition.Completion completion, String account,
      Result.Builder result) {
    return switch (completion) {
      case ISSUE_SESSION -> sessions.issue(account).compose(session -> sessions.issueTokens(session)
          .recover(failure -> sessions.end(session).transform(ignored -> Future.failedFuture(failure))).map(tokens -> {
            result.addSessions(SessionService.message(session)).addIssued(Any.pack(tokens));
            return null;
          }));
      case MARK_VERIFIED -> exchange.claimant().requireIdentifier().compose(identifiers::verify).map(identifier -> {
        result.addIdentifiers(IdentifierService.message(identifier));
        return null;
      });
      case ACTIVATE_FACTOR -> exchange.enrolment().isPresent()
          ? factors.activate(exchange.enrolment().get()).map(active -> {
            result.addFactors(FactorService.message(active));
            return null;
          })
          : factors.createRecoveryCodes(account).compose(codes -> factors.activate(codes.getFactor())
              .map(active -> {
                result.addFactors(FactorService.message(active)).addIssued(Any.pack(codes));
                return null;
              }));
      case END_SESSIONS -> sessions.endAll(account);
      case CREATE_ACCOUNT -> Future.succeededFuture();
      case COMPLETION_UNSPECIFIED, UNRECOGNIZED -> Future.failedFuture(new IllegalStateException("A completion of "
          + "the exchange " + exchange.name() + " has no kind, which the declaration was checked for."));
    };
  }
}
