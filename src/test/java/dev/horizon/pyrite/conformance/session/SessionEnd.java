package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.EndSession;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A session that its account ends no longer authenticates.")
final class SessionEnd extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new EndSession(ANNE));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.UNAUTHENTICATED, new Authenticate()), Requirement.SESSION_END);
  }
}
