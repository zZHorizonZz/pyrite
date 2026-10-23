package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import io.vertx.core.Future;
import java.util.Optional;

final class TotpMethod extends Method<TotpAnswer> {

  TotpMethod() {
    super(TotpAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, TotpAnswer answer) {
    String code = answer.getCode();
    return flow.factors(TotpFactor.class).compose(found -> {
      Future<Boolean> accepted = Future.succeededFuture(false);
      for (TotpFactor factor : found) {
        accepted = accepted.compose(right -> right ? Future.succeededFuture(true) : factor.accepts(code));
      }
      return accepted.map(right -> right ? Optional.of(Evidence.of(Evidence.Proof.POSSESSION)) : Optional.empty());
    });
  }
}
