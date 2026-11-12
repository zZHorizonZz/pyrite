package dev.horizon.pyrite.conformance.passkey;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectPasskeyCreation;
import dev.horizon.pyrite.conformance.condition.StartFlow;

@Summary("An enrolment excludes the passkeys that the account has.")
final class PasskeyExclusion extends PasskeyModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolPasskey();
    call(new StartFlow(ANNE, "enrol-passkey", ""));
    callAndStopOnFailure(new ExpectPasskeyCreation("acme.dev", 1), Requirement.PASSKEY_EXCLUSION);
  }
}
