package dev.horizon.pyrite.exchange;

import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The link of an account at a provider to the account of the exchange: a sign-in at the provider enrols it as a
 * pending factor. A provider account that an account links already fails the answer with {@code ALREADY_EXISTS}.
 */
final class ProviderEnrolment extends ProviderChallenge implements Enrolling<ProviderAnswer> {

  ProviderEnrolment(ProviderSet providers, FactorService factors) {
    super(providers, factors);
  }

  @Override
  public Evidence proves() {
    return Evidence.ENROLMENT;
  }

  @Override
  public Stage.Factor enrols() {
    return Stage.Factor.PROVIDER;
  }

  @Override
  public Future<Optional<Evidence>> verify(ExchangeRecord exchange, ProviderAnswer answer) {
    return providers.trade(exchange, this, answer).compose(account -> account.isEmpty()
        ? Future.succeededFuture(Optional.empty())
        : exchange.claimant().requireAccount()
            .compose(owner -> factors.enrolProvider(owner.getName(), account.get())).map(factor -> {
              exchange.enrol(factor);
              return Optional.of(Evidence.ENROLMENT);
            }));
  }
}
