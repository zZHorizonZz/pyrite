package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.MakeOlder;
import java.time.Duration;

@Summary("Each use of a session extends its idle lifetime.")
final class SessionIdleExtension extends SessionModule {

  @Override
  protected void run() {
    signIn();
    for (int week = 0; week < 3; week++) {
      call(new MakeOlder(Value.SESSION, Duration.ofDays(6)));
      callAndStopOnFailure(new Authenticate(), Requirement.SESSION_IDLE);
    }
  }
}
