package dev.horizon.pyrite.conformance.code;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerCode;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignIn;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A recovery sets a new password and ends every session.")
final class Recovery extends RecoveryModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new SignIn(EMAIL, PASSWORD));
    call(new StartExchange("", "recover", EMAIL));
    callAndStopOnFailure(new AnswerCode(), Requirement.RECOVERY_PASSWORD);
    callAndStopOnFailure(new AnswerPassword("a brand new passphrase"), Requirement.RECOVERY_PASSWORD);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.UNAUTHENTICATED, new Authenticate()), Requirement.RECOVERY_PASSWORD);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new SignIn(EMAIL, PASSWORD)),
        Requirement.RECOVERY_PASSWORD);
    callAndContinueOnFailure(new SignIn(EMAIL, "a brand new passphrase"), Requirement.RECOVERY_PASSWORD);
  }
}
