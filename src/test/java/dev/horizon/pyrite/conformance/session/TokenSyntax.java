package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectTokenSyntax;

@Summary("Text that is not a token is refused.")
final class TokenSyntax extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new ExpectTokenSyntax(), Requirement.SESSION_FORGERY);
  }
}
