package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.IdentifierService;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;
import java.util.Set;

/**
 * Signing in at a provider, which counts as a factor of its own.
 *
 * <p>
 * An exchange of an account takes the provider account that an active provider factor of the account links. An
 * exchange of the input {@code NONE} takes the account that links the provider account. For a provider account that no
 * account links, whose provider verified an email address that a verified identifier of an account holds, the email
 * match of the exchange decides: {@code SIGN_IN} signs in to that account, {@code CONFIRM} makes the exchange one of
 * that account without a factor, so that the stage asks for another; both link the provider account when the exchange
 * ends. Any other provider account the exchange keeps for {@code CREATE_ACCOUNT}, when it completes with it, and
 * refuses with {@code FAILED_PRECONDITION} otherwise.
 */
final class ProviderVerifier extends ProviderChallenge {

  private static final Evidence PROVES = Evidence.of(Set.of(Proof.FEDERATION), false);

  private final IdentifierService identifiers;

  ProviderVerifier(ProviderSet providers, FactorService factors, IdentifierService identifiers) {
    super(providers, factors);
    this.identifiers = identifiers;
  }

  @Override
  public Evidence proves() {
    return PROVES;
  }

  @Override
  public Future<Optional<Evidence>> verify(ExchangeRecord exchange, ProviderAnswer answer) {
    return providers.trade(exchange, this, answer).compose(account -> account.isEmpty()
        ? Future.succeededFuture(Optional.empty())
        : verify(exchange, account.get()));
  }

  private Future<Optional<Evidence>> verify(ExchangeRecord exchange, ProviderAccount account) {
    if (!exchange.claimant().accountName().isEmpty()) {
      return exchange.claimant().factors(Tables.Factor.Kind.PROVIDER)
          .compose(links -> FactorVerifier.first(links, link -> factors.verifyProvider(link, account)))
          .map(proofs -> proofs.isEmpty() ? Optional.empty() : Optional.of(PROVES));
    }
    if (exchange.definition().getInput() != ExchangeDefinition.Input.NONE) {
      return Future.succeededFuture(Optional.empty());
    }
    return factors.linked(account).compose(link -> {
      if (link.isPresent()) {
        exchange.identify(link.get().getParent());
        return Future.succeededFuture(Optional.of(PROVES));
      }
      return holder(exchange.definition(), account).compose(holder -> unlinked(exchange, account, holder));
    });
  }

  /**
   * Takes a provider account that no account links: to the account of its email address, when the exchange matches
   * emails, or for {@code CREATE_ACCOUNT}.
   */
  private Future<Optional<Evidence>> unlinked(ExchangeRecord exchange, ProviderAccount account,
      Optional<String> holder) {
    ExchangeDefinition definition = exchange.definition();
    if (holder.isPresent()) {
      exchange.identify(holder.get());
      exchange.providerAccount(account);
      return Future.succeededFuture(Optional.of(definition.getEmailMatch() == ExchangeDefinition.EmailMatch.SIGN_IN
          ? PROVES
          : Evidence.NOTHING));
    }
    if (definition.getCompletesList().contains(ExchangeDefinition.Completion.CREATE_ACCOUNT)) {
      exchange.providerAccount(account);
      return Future.succeededFuture(Optional.of(PROVES));
    }
    return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The " + account.getProvider()
        + " account " + account.getId() + " is linked to no account."));
  }

  /**
   * Finds the account whose verified identifier holds the email address that the provider verified, when the exchange
   * matches emails.
   */
  private Future<Optional<String>> holder(ExchangeDefinition definition, ProviderAccount account) {
    if (definition.getEmailMatch() == ExchangeDefinition.EmailMatch.EMAIL_MATCH_UNSPECIFIED
        || !account.getEmailVerified() || account.getEmail().isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return identifiers.find("", account.getEmail()).map(found -> found.filter(Tables.Identifier::getVerified)
        .map(Tables.Identifier::getParent));
  }
}
