package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Any;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.authentication.v1.TotpProvisioning;
import io.vertx.core.Future;
import java.util.Optional;

final class TotpEnrolment extends Method<TotpAnswer> {

  TotpEnrolment() {
    super(TotpAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Delivery>> prepare(FlowImpl flow) {
    return flow.account().compose(account -> account.orElseThrow().enrolTotp()).map(factor -> {
      flow.enrol(factor);
      return Optional.empty();
    });
  }

  @Override
  Future<Challenge> challenge(FlowImpl flow) {
    return flow.enrolment(TotpFactor.class).compose(factor -> factor.uri(factor.row().getParent()))
        .map(uri -> Challenge.newBuilder().setMethod(name())
            .setParameters(Any.pack(TotpProvisioning.newBuilder().setUri(uri).build())).build());
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, TotpAnswer answer) {
    String code = answer.getCode();
    return flow.enrolment(TotpFactor.class).compose(factor -> factor.accepts(code))
        .map(right -> right ? Optional.of(Evidence.of(Evidence.Proof.ENROLLED)) : Optional.empty());
  }
}
