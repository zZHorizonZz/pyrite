package dev.horizon.pyrite.conformance.factor;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Two answers of the same kind are not two factors.")
final class SameKindFactor extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    passPasswordStage();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerPassword(PASSWORD)),
        Requirement.FACTOR_KIND);
  }
}
