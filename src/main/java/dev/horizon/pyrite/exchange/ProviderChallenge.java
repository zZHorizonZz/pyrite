package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.identity.FactorService;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * What signing in at a provider and linking an account at one share: the answer, the secrets that a stage prepares,
 * and the challenge that holds the authorization URL of each provider.
 */
abstract sealed class ProviderChallenge implements Verifier<ProviderAnswer>
    permits ProviderVerifier, ProviderEnrolment {

  final ProviderSet providers;
  final FactorService factors;

  ProviderChallenge(ProviderSet providers, FactorService factors) {
    this.providers = providers;
    this.factors = factors;
  }

  @Override
  public ProviderAnswer prototype() {
    return ProviderAnswer.getDefaultInstance();
  }

  @Override
  public Future<Optional<Delivery>> prepare(ExchangeRecord exchange) {
    providers.prepare(exchange, this);
    return Future.succeededFuture(Optional.empty());
  }

  @Override
  public Future<Challenge> challenge(ExchangeRecord exchange) {
    return Future.succeededFuture(Challenge.newBuilder().setMethod(name())
        .setParameters(Any.pack(providers.redirects(exchange, this))).build());
  }
}
