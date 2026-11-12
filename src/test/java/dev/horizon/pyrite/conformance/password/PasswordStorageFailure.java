package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.RenameTable;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An account that cannot be read fails the sign-in as an internal error, never as a wrong password.")
final class PasswordStorageFailure extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow("", "sign-in", EMAIL));
    call(new RenameTable("pyrite_account", "pyrite_account_away"));
    try {
      callAndStopOnFailure(new ExpectStatus(GrpcStatus.INTERNAL, new AnswerPassword(PASSWORD)), Requirement.FLOW_STORAGE);
    } finally {
      call(new RenameTable("pyrite_account_away", "pyrite_account"));
    }
  }
}
