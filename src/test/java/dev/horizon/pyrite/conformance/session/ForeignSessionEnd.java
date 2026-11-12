package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.EndSession;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Another account does not end a session.")
final class ForeignSessionEnd extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new EndSession(BOB)), Requirement.SESSION_OWNER);
    callAndStopOnFailure(new Authenticate(), Requirement.SESSION_OWNER);
  }
}
