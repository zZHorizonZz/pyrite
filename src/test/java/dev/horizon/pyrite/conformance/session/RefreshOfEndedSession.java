package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.EndSession;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.Refresh;
import io.vertx.grpc.common.GrpcStatus;

@Summary("The refresh token of a session that its account ends is refused.")
final class RefreshOfEndedSession extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new EndSession(ANNE));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.UNAUTHENTICATED, new Refresh()), Requirement.SESSION_END);
  }
}
