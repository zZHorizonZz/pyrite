package dev.horizon.pyrite.exchange;

import com.google.protobuf.Any;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
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

  private final FactorService factors;

  TotpEnrolment(FactorService factors) {
    this.factors = factors;
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
  public Future<Optional<Delivery>> prepare(ExchangeRecord exchange) {
    return exchange.claimant().requireAccount().compose(account -> factors.enrolTotp(account.getName()))
        .map(secret -> {
          exchange.enrol(secret);
          return Optional.empty();
        });
  }

  @Override
  public Future<Challenge> challenge(ExchangeRecord exchange) {
    return pending(exchange).compose(factors::totpUri)
        .map(uri -> Challenge.newBuilder().setMethod(name())
            .setParameters(Any.pack(TotpProvisioning.newBuilder().setUri(uri).build())).build());
  }

  @Override
  public Future<Optional<Evidence>> verify(ExchangeRecord exchange, TotpAnswer answer) {
    return pending(exchange).compose(secret -> factors.verifyTotp(secret, answer.getCode()))
        .map(proofs -> proofs.isEmpty() ? Optional.empty() : Optional.of(Evidence.ENROLMENT));
  }

  private Future<Tables.Factor> pending(ExchangeRecord exchange) {
    PyriteException none = new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The exchange " + exchange.name()
        + " enrols no TOTP secret.");
    return exchange.enrolment()
        .map(name -> factors.factor(name)
            .compose(factor -> factor.getKind() == Tables.Factor.Kind.TOTP ? Future.succeededFuture(factor)
                : Future.<Tables.Factor> failedFuture(none)))
        .orElseGet(() -> Future.failedFuture(none));
  }
}
