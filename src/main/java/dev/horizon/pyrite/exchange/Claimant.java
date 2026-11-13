package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.identity.AccountService;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.IdentifierService;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Optional;

/**
 * Who an exchange is for: the name of the account that it names, and of the identifier that it starts from. Each row is
 * read when an answer asks for it, so an account deleted while the exchange goes on is gone for the answers after.
 */
final class Claimant {

  private final String exchange;
  private final ExchangeDefinition.Input input;
  private final Optional<String> account;
  private final Optional<String> identifier;
  private final AccountService accounts;
  private final IdentifierService identifiers;
  private final FactorService factors;

  /**
   * Creates the claimant of an exchange.
   *
   * @param exchange the name of the exchange, for the messages of failures
   * @param input what the exchange starts from
   * @param account the name of the account, or nothing for an exchange that names none
   * @param identifier the name of the identifier, or nothing for an exchange that names none
   */
  Claimant(String exchange, ExchangeDefinition.Input input, Optional<String> account, Optional<String> identifier,
      AccountService accounts, IdentifierService identifiers, FactorService factors) {
    this.exchange = exchange;
    this.input = input;
    this.account = account;
    this.identifier = identifier;
    this.accounts = accounts;
    this.identifiers = identifiers;
    this.factors = factors;
  }

  /**
   * Gives the claimant of an account that an answer named, such as the account that links a provider account.
   */
  Claimant withAccount(String name) {
    return new Claimant(exchange, input, Optional.of(name), identifier, accounts, identifiers, factors);
  }

  /**
   * Gives the name of the account, such as {@code accounts/anne}, or an empty text for an exchange that names none.
   */
  String accountName() {
    return account.orElse("");
  }

  /**
   * Gives the account, or nothing when the exchange names none, or the account was deleted.
   */
  Future<Optional<Tables.Account>> account() {
    if (account.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return accounts.account(account.get()).map(Optional::of)
        .recover(failure -> failure instanceof PyriteException refusal && refusal.status() == GrpcStatus.NOT_FOUND
            ? Future.succeededFuture(Optional.empty())
            : Future.failedFuture(failure));
  }

  /**
   * Gives the account, or a failure with {@code FAILED_PRECONDITION} when the exchange names none, or the account was
   * deleted.
   */
  Future<Tables.Account> requireAccount() {
    return account().compose(found -> found.isPresent() ? Future.succeededFuture(found.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange " + exchange
            + " has no account: it names none, or the account was deleted.")));
  }

  /**
   * Gives the active factors of a kind of the account; none for an exchange without an account.
   */
  Future<List<Tables.Factor>> factors(Tables.Factor.Kind kind) {
    return account().compose(found -> found.isEmpty() ? Future.succeededFuture(List.<Tables.Factor> of())
        : factors.activeFactors(found.get().getName(), kind));
  }

  /**
   * Gives the identifier, or nothing when the exchange names none, or the identifier was removed.
   */
  Future<Optional<Tables.Identifier>> identifier() {
    if (identifier.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return identifiers.identifier(identifier.get());
  }

  /**
   * Gives the identifier, or a failure with {@code FAILED_PRECONDITION} when the exchange names none, or the
   * identifier was removed.
   */
  Future<Tables.Identifier> requireIdentifier() {
    return identifier().compose(found -> found.isPresent() ? Future.succeededFuture(found.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange " + exchange
            + " has no identifier: it names none, or the identifier was removed.")));
  }

  /**
   * Gives the identifier that a code goes to: that of the exchange, if its kind receives codes, and it is verified or
   * the exchange starts from a subject and an identifier of its account. An exchange without an account has none, so
   * an identifier that no account has receives no code.
   */
  Future<Optional<Tables.Identifier>> recipient() {
    if (account.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return identifier().map(found -> found.filter(candidate -> identifiers.isDeliverable(candidate)
        && (candidate.getVerified() || input == ExchangeDefinition.Input.SUBJECT_IDENTIFIER)));
  }
}
