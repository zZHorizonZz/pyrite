package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The link of an account at a provider to the account of the exchange: a sign-in at the provider enrols it as a
 * pending factor. A provider account that an account links already fails the answer with {@code ALREADY_EXISTS}.
 */
final class ProviderEnrolment implements Enrolling<ProviderAnswer> {

  private final ProviderSet providers;
  private final CredentialType credentials;

  ProviderEnrolment(ProviderSet providers, CredentialType credentials) {
    this.providers = providers;
    this.credentials = credentials;
  }

  @Override
  public ProviderAnswer prototype() {
    return ProviderAnswer.getDefaultInstance();
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
        : exchange.claimant().requireAccount()
            .compose(owner -> credentials.enrolProvider(owner.name(), account.get())).map(credential -> {
              exchange.enrol(credential);
              return Optional.of(Evidence.ENROLMENT);
            }));
  }
}
