package dev.horizon.pyrite.conformance.passkey;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPasskeyCreation;
import dev.horizon.pyrite.conformance.condition.CreatePasskeyClient;
import dev.horizon.pyrite.conformance.condition.ExpectFactorResult;
import dev.horizon.pyrite.conformance.condition.ExpectPasskeyCreation;
import dev.horizon.pyrite.conformance.condition.StartFlow;

@Summary("A passkey enrols with the creation options that the flow gives.")
final class PasskeyRegistration extends PasskeyModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreatePasskeyClient(ORIGIN));
    call(new StartFlow(ANNE, "enrol-passkey", ""));
    callAndContinueOnFailure(new ExpectPasskeyCreation("acme.dev", 0), Requirement.PASSKEY_ENROLMENT);
    callAndStopOnFailure(new AnswerPasskeyCreation(), Requirement.PASSKEY_ENROLMENT);
    callAndStopOnFailure(new ExpectFactorResult(Factor.Kind.PASSKEY, "accounts/anne"), Requirement.PASSKEY_ENROLMENT);
  }
}
