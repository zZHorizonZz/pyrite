package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.ProviderAccount;
import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.identity.Identifier;
import dev.horizon.pyrite.identity.IdentifierType;
import dev.horizon.pyrite.identity.Proof;
import dev.horizon.pyrite.schema.v1.ExchangeDefinition;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;
import java.util.Set;

/**
 * Signing in at a provider, such as Google or GitHub, which counts as a factor of its own. The challenge holds the
 * authorization URL of each provider, and the answer the code and the state that the provider gave the callback.
 *
 * <p>
 * An exchange of an account takes the provider account that the account links. An exchange of the input
 * {@code NONE} takes the account that links the provider account. For a provider account that no account links, whose
 * provider verified an email address that a verified identifier of an account holds, the email match of the exchange
 * decides: {@code SIGN_IN} signs in to that account, {@code CONFIRM} makes the exchange one of that account without a
 * factor, so that the stage asks for another; both link the provider account when the exchange ends. Any other
 * provider account the exchange keeps for {@code CREATE_ACCOUNT}, when it completes with it, and refuses with
 * {@code FAILED_PRECONDITION} otherwise.
 */
final class ProviderVerifier implements Verifier<ProviderAnswer> {

  private static final Evidence PROVES = Evidence.of(Set.of(Proof.FEDERATION), false);

  private final ProviderSet providers;
  private final CredentialType credentials;
  private final IdentifierType identifiers;

  ProviderVerifier(ProviderSet providers, CredentialType credentials, IdentifierType identifiers) {
    this.providers = providers;
    this.credentials = credentials;
    this.identifiers = identifiers;
  }

  @Override
  public ProviderAnswer prototype() {
    return ProviderAnswer.getDefaultInstance();
  }

  @Override
  public Evidence proves() {
    return PROVES;
  }

  @Override
  public Future<Optional<Delivery>> prepare(Exchange exchange) {
    providers.prepare(exchange, this);
    return Future.succeededFuture(Optional.empty());
  }

  @Override
  public Future<Challenge> challenge(Exchange exchange) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(providers.redirects(exchange, this))).build());
  }

  @Override
  public Future<Optional<Evidence>> verify(Exchange exchange, ProviderAnswer answer) {
    return providers.trade(exchange, this, answer).compose(account -> account.isEmpty()
        ? Future.succeededFuture(Optional.empty())
        : credentials.linked(account.get()).compose(linked -> {
          String claimant = exchange.claimant().accountName();
          if (!claimant.isEmpty()) {
            return Future.succeededFuture(linked.filter(credential -> credential.account().equals(claimant))
                .map(credential -> PROVES));
          }
          ExchangeDefinition definition = exchange.definition();
          if (definition.getInput() != ExchangeDefinition.Input.NONE) {
            return Future.succeededFuture(Optional.empty());
          }
          if (linked.isPresent()) {
            exchange.identify(linked.get().account());
            return Future.succeededFuture(Optional.of(PROVES));
          }
          return holder(definition, account.get()).compose(holder -> {
            if (holder.isPresent()) {
              exchange.identify(holder.get());
              exchange.providerAccount(account.get());
              return Future.succeededFuture(
                  Optional.of(definition.getEmailMatch() == ExchangeDefinition.EmailMatch.SIGN_IN ? PROVES : Evidence.NOTHING));
            }
            if (definition.getCompletesList().contains(ExchangeDefinition.Completion.CREATE_ACCOUNT)) {
              exchange.providerAccount(account.get());
              return Future.succeededFuture(Optional.of(PROVES));
            }
            return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The "
                + account.get().getProvider() + " account " + account.get().getId() + " is linked to no account."));
          });
        }));
  }

  /**
   * Finds the account whose verified identifier holds the email address that the provider verified, when the exchange
   * matches emails.
   */
  private Future<Optional<String>> holder(ExchangeDefinition definition, ProviderAccount account) {
    if (definition.getEmailMatch() == ExchangeDefinition.EmailMatch.EMAIL_MATCH_UNSPECIFIED || !account
        .getEmailVerified() || account.getEmail().isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return identifiers.find("", account.getEmail()).map(found -> found.filter(Identifier::isVerified)
        .map(Identifier::account));
  }
}
