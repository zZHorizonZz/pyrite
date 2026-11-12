package dev.horizon.pyrite.flow;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import dev.horizon.pyrite.identity.TotpCredential;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The enrolment of a TOTP authenticator: the flow enrols a new secret as pending when it reaches the stage, the
 * challenge holds its {@code otpauth} URI, and a code of the secret proves that the app holds it.
 */
final class TotpEnrolment extends Authentication<TotpAnswer> {

  TotpEnrolment() {
    super(TotpAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(Flow flow) {
    return flow.requireAccount().compose(account -> flow.type().credentials().enrolTotp(account.name()))
        .map(factor -> {
          flow.enrol(factor);
          return Optional.empty();
        });
  }

  @Override
  Future<Challenge> challenge(Flow flow) {
    return flow.enrolment(TotpCredential.class).compose(factor -> factor.uri(factor.account()))
        .map(uri -> Challenge.newBuilder().setMethod(name())
            .setParameters(Any.pack(TotpProvisioning.newBuilder().setUri(uri).build())).build());
  }

  @Override
  Future<Optional<Evidence>> evaluate(Flow flow, TotpAnswer answer) {
    String code = answer.getCode();
    return flow.enrolment(TotpCredential.class).compose(factor -> factor.accepts(code))
        .map(right -> right ? Optional.of(Evidence.of(Evidence.Proof.ENROLMENT)) : Optional.empty());
  }
}
