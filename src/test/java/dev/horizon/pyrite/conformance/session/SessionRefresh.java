package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.Refresh;

@Summary("A refresh trades the refresh token for new tokens, whose access token authenticates.")
final class SessionRefresh extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new Refresh(), Requirement.SESSION_REFRESH);
    callAndStopOnFailure(new Authenticate(), Requirement.SESSION_REFRESH);
  }
}
