package dev.horizon.pyrite.conformance.handler;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.EndSession;
import dev.horizon.pyrite.conformance.condition.ExpectSessionObservation;
import dev.horizon.pyrite.conformance.condition.ObserveSessions;
import dev.horizon.pyrite.conformance.condition.SignIn;

@Summary("The handlers of sessions hear of each session that is issued and ended.")
final class SessionObservation extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new ObserveSessions());
    call(new SignIn(EMAIL, PASSWORD));
    call(new EndSession(ANNE));
    callAndStopOnFailure(new ExpectSessionObservation(), Requirement.HANDLER_SESSION);
  }
}
