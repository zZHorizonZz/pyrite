package dev.horizon.pyrite.conformance.code;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerCode;
import dev.horizon.pyrite.conformance.condition.AnswerWrongCode;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Guessing the code exhausts the flow.")
final class CodeGuessing extends RecoveryModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new StartFlow("", "recover", EMAIL));
    for (int attempt = 0; attempt < 3; attempt++) {
      callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerWrongCode()), Requirement.FLOW_ANSWERS);
    }
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.RESOURCE_EXHAUSTED, new AnswerCode()), Requirement.FLOW_ANSWERS);
  }
}
