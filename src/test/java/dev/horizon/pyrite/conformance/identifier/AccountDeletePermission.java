package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.DeleteAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GetAccount;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Nobody but its owner deletes an account.")
final class AccountDeletePermission extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccountOfKind("mallory", "HUMAN"));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new DeleteAccount("user:mallory",
        "accounts/anne")), Requirement.ACCOUNT_DELETE);
    callAndStopOnFailure(new GetAccount(ANNE, "accounts/anne"), Requirement.ACCOUNT_DELETE);
  }
}
