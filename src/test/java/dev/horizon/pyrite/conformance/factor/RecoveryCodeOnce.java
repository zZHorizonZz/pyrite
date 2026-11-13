package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerRecoveryCode;
import dev.horizon.pyrite.conformance.condition.EnrolRecoveryCodes;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Each recovery code is a second factor once.")
final class RecoveryCodeOnce extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new EnrolRecoveryCodes(ANNE, Value.RECOVERY_CODES));
    passPasswordStage();
    callAndStopOnFailure(new AnswerRecoveryCode(Value.RECOVERY_CODES, 0), Requirement.RECOVERY_CODES);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.RECOVERY_CODES);
    passPasswordStage();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerRecoveryCode(
        Value.RECOVERY_CODES, 0)), Requirement.RECOVERY_CODES);
    passPasswordStage();
    callAndStopOnFailure(new AnswerRecoveryCode(Value.RECOVERY_CODES, 1), Requirement.RECOVERY_CODES);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.RECOVERY_CODES);
  }
}
