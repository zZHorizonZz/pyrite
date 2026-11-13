package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.EndSession;
import dev.horizon.pyrite.conformance.condition.ExpectPurgeCount;
import dev.horizon.pyrite.conformance.condition.Purge;

@Summary("The purge deletes the row of an ended session.")
final class SessionPurge extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new EndSession(ANNE));
    call(new Purge());
    callAndStopOnFailure(new ExpectPurgeCount(1), Requirement.PURGE_SESSION);
  }
}
