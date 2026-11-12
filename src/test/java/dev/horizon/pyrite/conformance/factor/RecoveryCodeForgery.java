package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.EnrolRecoveryCodes;
import dev.horizon.pyrite.conformance.condition.ExpectRecoveryCodeForgeryRefusal;

@Summary("A recovery code that was not issued is wrong.")
final class RecoveryCodeForgery extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new EnrolRecoveryCodes(ANNE, Value.RECOVERY_CODES));
    callAndStopOnFailure(new ExpectRecoveryCodeForgeryRefusal(EMAIL, PASSWORD), Requirement.RECOVERY_CODES);
  }
}
