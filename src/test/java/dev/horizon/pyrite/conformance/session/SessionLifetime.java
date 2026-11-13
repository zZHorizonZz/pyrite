package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.MakeOlder;
import dev.horizon.pyrite.conformance.condition.Refresh;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A session that is refreshed ends at its lifetime all the same.")
final class SessionLifetime extends SessionModule {

  @Override
  protected void run() {
    signIn();
    for (int week = 0; week < 4; week++) {
      call(new MakeOlder(Value.SESSION, Duration.ofDays(6)));
      call(new Refresh());
    }
    call(new MakeOlder(Value.SESSION, Duration.ofDays(6)));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.UNAUTHENTICATED, new Refresh()), Requirement.SESSION_LIFETIME);
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.UNAUTHENTICATED, new Authenticate()), Requirement.SESSION_LIFETIME);
  }
}
