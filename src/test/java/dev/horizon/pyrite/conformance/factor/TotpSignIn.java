package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerTotp;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectChallenge;
import dev.horizon.pyrite.conformance.condition.ExpectNoResult;
import dev.horizon.pyrite.conformance.condition.ExpectPrincipal;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import java.time.Duration;

@Summary("A password and a code sign in, and the password alone does not.")
final class TotpSignIn extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolTotp();
    passPasswordStage();
    callAndStopOnFailure(new ExpectNoResult(), Requirement.TOTP_SIGN_IN);
    callAndContinueOnFailure(new ExpectChallenge("pyrite.authentication.v1.TotpAnswer", true), Requirement.TOTP_SIGN_IN);
    callAndContinueOnFailure(new ExpectChallenge("pyrite.authentication.v1.PasswordAnswer", false), Requirement.TOTP_SIGN_IN);
    callAndStopOnFailure(new AnswerTotp(Value.TOTP_SECRET, 0), Requirement.TOTP_SIGN_IN);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.TOTP_SIGN_IN);
    call(new Authenticate());
    callAndStopOnFailure(new ExpectPrincipal(ANNE, "accounts/anne", Duration.ofDays(30)), Requirement.TOTP_SIGN_IN);
  }
}
