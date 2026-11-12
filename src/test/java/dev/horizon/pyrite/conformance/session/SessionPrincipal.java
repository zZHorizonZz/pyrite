package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectPrincipal;
import java.time.Duration;

@Summary("A session token gives its principal: the subject, the account, the session and the expiry.")
final class SessionPrincipal extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new Authenticate());
    callAndStopOnFailure(new ExpectPrincipal(ANNE, "accounts/anne", Duration.ofDays(30)), Requirement.SESSION_PRINCIPAL);
  }
}
