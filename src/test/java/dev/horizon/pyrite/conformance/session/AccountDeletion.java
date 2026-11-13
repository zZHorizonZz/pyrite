package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.DeleteAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A session of a deleted account no longer authenticates.")
final class AccountDeletion extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new DeleteAccount(ANNE, "accounts/anne"));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.UNAUTHENTICATED, new Authenticate()), Requirement.SESSION_END);
  }
}
