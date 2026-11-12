package dev.horizon.pyrite.conformance.code;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerWrongCode;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignIn;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A wrong code of a recovery leaves the password as it was.")
final class RecoveryWrongCode extends RecoveryModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new StartFlow("", "recover", EMAIL));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerWrongCode()),
        Requirement.RECOVERY_PASSWORD);
    callAndContinueOnFailure(new SignIn(EMAIL, PASSWORD), Requirement.RECOVERY_PASSWORD);
  }
}
