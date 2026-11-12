package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.EnrolRecoveryCodes;
import dev.horizon.pyrite.conformance.condition.ExpectRecoveryCodes;

@Summary("An enrolment issues ten different recovery codes.")
final class RecoveryCodeIssue extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndStopOnFailure(new EnrolRecoveryCodes(ANNE, Value.RECOVERY_CODES), Requirement.RECOVERY_CODES);
    callAndStopOnFailure(new ExpectRecoveryCodes(10), Requirement.RECOVERY_CODES);
  }
}
