package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.identity.Account;
import dev.horizon.pyrite.identity.AccountType;
import dev.horizon.pyrite.identity.Credential;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.identity.Identifier;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
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
  private final AccountType accounts;
  private final CredentialType credentials;

  ExchangeEnd(ExchangeInterceptor interceptor, AccountType accounts) {
    this.interceptor = interceptor;
    this.accounts = accounts;
    this.credentials = accounts.credentials();
  }

  /**
   * Ends an exchange.
   *
   * @return the result, with the account and what the completions issued, verified or activated; or a failure with
   * {@code FAILED_PRECONDITION} when the account was deleted while the exchange went on, or with the cause that an
   * interceptor of the end gave
   */
  Future<Result> run(Exchange exchange) {
    Result.Builder result = Result.newBuilder();
    return exchange.claimant().account().compose(account -> interceptor.end(exchange, account)
        .compose(ignored -> account.isEmpty() && exchange.definition().getCompletesList()
            .contains(ExchangeDefinition.Completion.CREATE_ACCOUNT) ? create(exchange, result).map(Optional::of)
                : Future.succeededFuture(account)))
        .compose(found -> {
          if (found.isEmpty()) {
            return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange "
                + exchange.name() + " completes for an account, but has none: the account was deleted while the exchange"
                + " went on."));
          }
          result.setAccount(found.get().name());
          Future<Void> completing = exchange.claimant().accountName().isEmpty() ? Future.succeededFuture()
              : link(exchange, found.get(), result);
          for (ExchangeDefinition.Completion completion : exchange.definition().getCompletesList()) {
            completing = completing.compose(done -> complete(exchange, completion, found.get(), result));
          }
          return completing.map(done -> result.build());
        });
  }

  /**
   * Links the provider account that the exchange found an account for by its email address, if it did.
   */
  private Future<Void> link(Exchange exchange, Account account, Result.Builder result) {
    return exchange.providerAccount().map(provider -> credentials.enrolProvider(account.name(), provider)
        .compose(Credential::activate).<Void> map(active -> {
          result.addFactors(active.message());
          return null;
        })).orElseGet(Future::succeededFuture);
  }

  /**
   * Creates an account for the provider account of the exchange, with a random id, and links the provider account to
   * it.
   */
  private Future<Account> create(Exchange exchange, Result.Builder result) {
    Optional<ProviderAccount> provider = exchange.providerAccount();
    if (provider.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange " + exchange.name()
          + " creates an account, but no answer named a provider account."));
    }
    String id = "u" + UUID.randomUUID().toString().replace("-", "");
    return accounts.create(new Subject(TypeSystem.USER, id), id,
        dev.horizon.pyrite.authentication.v1.Account.Kind.HUMAN)
        .compose(account -> credentials.enrolProvider(account.name(), provider.get())
            .compose(Credential::activate).map(active -> {
              result.addFactors(active.message());
              return account;
            }));
  }

  private Future<Void> complete(Exchange exchange, ExchangeDefinition.Completion completion, Account account,
      Result.Builder result) {
    return switch (completion) {
      case ISSUE_SESSION -> account.issueSession().compose(session -> session.issueTokens().map(tokens -> {
        result.addSessions(session.message()).addIssued(Any.pack(tokens));
        return null;
      }));
      case MARK_VERIFIED -> exchange.claimant().requireIdentifier().compose(Identifier::verify).map(identifier -> {
        result.addIdentifiers(identifier.message());
        return null;
      });
      case ACTIVATE_FACTOR -> exchange.enrolment().isPresent()
          ? credentials.credential(exchange.enrolment().get()).compose(Credential::activate).map(active -> {
            result.addFactors(active.message());
            return null;
          })
          : credentials.createRecoveryCodes(account.name()).compose(codes -> codes.credential().activate()
              .map(active -> {
                result.addFactors(active.message()).addIssued(codes.toAny());
                return null;
              }));
      case END_SESSIONS -> account.endSessions();
      case CREATE_ACCOUNT -> Future.succeededFuture();
      case COMPLETION_UNSPECIFIED, UNRECOGNIZED -> Future.failedFuture(new IllegalStateException("A completion of "
          + "the exchange " + exchange.name() + " has no kind, which the declaration was checked for."));
    };
  }
}
