package dev.horizon.pyrite.conformance.password;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectChallenge;
import dev.horizon.pyrite.conformance.condition.ExpectPrincipal;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import java.time.Duration;

@Summary("The right password issues a session of the account.")
final class PasswordSignIn extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in", EMAIL));
    callAndContinueOnFailure(new ExpectChallenge("pyrite.authentication.v1.PasswordAnswer", true),
        Requirement.PASSWORD_SIGN_IN);
    callAndStopOnFailure(new AnswerPassword(PASSWORD), Requirement.PASSWORD_SIGN_IN);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.PASSWORD_SIGN_IN);
    call(new Authenticate());
    callAndStopOnFailure(new ExpectPrincipal(ANNE, "accounts/anne", Duration.ofDays(30)), Requirement.PASSWORD_SIGN_IN);
  }
}
