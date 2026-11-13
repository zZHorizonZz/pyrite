package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectSessionRead;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GetSession;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A session is read by its account, and by nobody else.")
final class SessionRead extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new Authenticate());
    call(new GetSession(ANNE));
    callAndContinueOnFailure(new ExpectSessionRead("accounts/anne"), Requirement.SESSION_READ);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new GetSession(BOB)), Requirement.SESSION_READ);
  }
}
