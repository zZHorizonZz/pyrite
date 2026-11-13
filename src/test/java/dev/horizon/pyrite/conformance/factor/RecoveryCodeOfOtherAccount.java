package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerRecoveryCode;
import dev.horizon.pyrite.conformance.condition.CreateAccount;
import dev.horizon.pyrite.conformance.condition.EnrolRecoveryCodes;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A recovery code of another account is wrong.")
final class RecoveryCodeOfOtherAccount extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccount("bob", "bob@acme.dev", "another long passphrase"));
    call(new EnrolRecoveryCodes("user:bob", Value.OTHER_RECOVERY_CODES));
    call(new EnrolRecoveryCodes(ANNE, Value.RECOVERY_CODES));
    passPasswordStage();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerRecoveryCode(
        Value.OTHER_RECOVERY_CODES, 0)), Requirement.RECOVERY_CODES);
  }
}
