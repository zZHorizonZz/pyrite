package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.MakeOlder;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("An unused session ends after its idle lifetime.")
final class SessionIdleLifetime extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new MakeOlder(Value.SESSION, Duration.ofDays(7)));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.UNAUTHENTICATED, new Authenticate()), Requirement.SESSION_IDLE);
  }
}
