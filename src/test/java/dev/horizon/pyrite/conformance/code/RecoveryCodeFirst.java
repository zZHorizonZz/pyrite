package dev.horizon.pyrite.conformance.code;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignIn;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A recovery does not enrol a password before the code is proven.")
final class RecoveryCodeFirst extends RecoveryModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new StartFlow("", "recover", EMAIL));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerPassword("a brand new passphrase")),
        Requirement.RECOVERY_PASSWORD);
    callAndContinueOnFailure(new SignIn(EMAIL, PASSWORD), Requirement.RECOVERY_PASSWORD);
  }
}
