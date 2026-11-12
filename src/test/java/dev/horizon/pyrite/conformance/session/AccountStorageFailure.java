package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.RenameTable;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A session whose account cannot be read fails as an internal error, never as signed out.")
final class AccountStorageFailure extends SessionModule {

  @Override
  protected void run() {
    signIn();
    call(new RenameTable("pyrite_account", "pyrite_account_away"));
    try {
      callAndStopOnFailure(new ExpectStatus(GrpcStatus.INTERNAL, new Authenticate()), Requirement.SESSION_STORAGE);
    } finally {
      call(new RenameTable("pyrite_account_away", "pyrite_account"));
    }
  }
}
