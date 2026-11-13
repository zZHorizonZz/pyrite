package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.identity.TotpCredential;
import dev.horizon.pyrite.schema.v1.Stage;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;

/**
 * The enrolment of a TOTP authenticator: the exchange enrols a new secret as pending when it reaches the stage, the
 * challenge holds its {@code otpauth} URI, and a code of the secret, which the secret verifies as any TOTP code, proves
 * that the app holds it.
 */
final class TotpEnrolment implements Enrolling<TotpAnswer> {

  private final CredentialType credentials;

  TotpEnrolment(CredentialType credentials) {
    this.credentials = credentials;
  }

  @Override
  public TotpAnswer prototype() {
    return TotpAnswer.getDefaultInstance();
  }

  @Override
  public Evidence proves() {
    return Evidence.ENROLMENT;
  }

  @Override
  public Stage.Factor enrols() {
    return Stage.Factor.TOTP;
  }

  @Override
  public Future<Optional<Delivery>> prepare(Exchange exchange) {
    return exchange.claimant().requireAccount().compose(account -> credentials.enrolTotp(account.name()))
        .map(secret -> {
          exchange.enrol(secret);
          return Optional.empty();
        });
  }

  @Override
  public Future<Challenge> challenge(Exchange exchange) {
    return pending(exchange).compose(secret -> secret.uri(secret.account()))
        .map(uri -> Challenge.newBuilder().setMethod(name())
            .setParameters(Any.pack(TotpProvisioning.newBuilder().setUri(uri).build())).build());
  }

  @Override
  public Future<Optional<Evidence>> verify(Exchange exchange, TotpAnswer answer) {
    return pending(exchange).compose(secret -> secret.verify(answer, new byte[0]))
        .map(proven -> proven.isEmpty() ? Optional.empty() : Optional.of(Evidence.ENROLMENT));
  }

  private Future<TotpCredential> pending(Exchange exchange) {
    return exchange.enrolment().map(name -> credentials.credential(name).map(TotpCredential.class::cast))
        .orElseGet(() -> Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange "
            + exchange.name() + " enrols no TOTP secret.")));
  }
}
