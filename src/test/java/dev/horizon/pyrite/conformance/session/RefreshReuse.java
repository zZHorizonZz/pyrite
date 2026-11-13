package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectSpentRefreshRefusal;
import dev.horizon.pyrite.conformance.condition.Refresh;

@Summary("A refresh token works once; one that comes back after its trade ends the session.")
final class RefreshReuse extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new Refresh());
    callAndStopOnFailure(new ExpectSpentRefreshRefusal(), Requirement.SESSION_REFRESH_REUSE);
  }
}
