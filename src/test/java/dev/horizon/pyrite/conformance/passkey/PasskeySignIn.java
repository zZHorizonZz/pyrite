package dev.horizon.pyrite.conformance.passkey;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPasskeyRequest;
import dev.horizon.pyrite.conformance.condition.ExpectChallenge;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("A passkey of the account signs in.")
final class PasskeySignIn extends PasskeyModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolPasskey();
    call(new StartExchange("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectChallenge("pyrite.authentication.v1.PasskeyAnswer", true),
        Requirement.PASSKEY_SIGN_IN);
    callAndStopOnFailure(new AnswerPasskeyRequest(), Requirement.PASSKEY_SIGN_IN);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.PASSKEY_SIGN_IN);
  }
}
