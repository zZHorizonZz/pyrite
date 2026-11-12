package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectNewToken;

@Summary("Each sign-in issues a token of its own.")
final class TokenPerSignIn extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new ExpectNewToken(EMAIL, PASSWORD), Requirement.SESSION_ISSUE);
  }
}
