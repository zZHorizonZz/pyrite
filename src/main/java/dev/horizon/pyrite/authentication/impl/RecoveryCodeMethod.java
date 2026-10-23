package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import io.vertx.core.Future;
import java.util.Optional;

final class RecoveryCodeMethod extends Method<RecoveryCodeAnswer> {

  RecoveryCodeMethod() {
    super(RecoveryCodeAnswer.getDefaultInstance());
  }

  @Override
  Future<Optional<Evidence>> evaluate(FlowImpl flow, RecoveryCodeAnswer answer) {
    String code = answer.getCode();
    return flow.factors(RecoveryCodesFactor.class).compose(found -> found.isEmpty()
        ? Future.succeededFuture(Optional.empty())
        : found.getFirst().use(code).map(right -> right ? Optional.of(Evidence.of(Evidence.Proof.POSSESSION))
            : Optional.empty()));
  }
}
