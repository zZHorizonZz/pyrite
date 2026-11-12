package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GetAccount;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An account owns itself and nobody else does.")
final class AccountOwner extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccountOfKind("mallory", "HUMAN"));
    call(new Check(ANNE, "accounts/anne", "owner"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.ACCOUNT_OWNER);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new GetAccount("user:mallory",
        "accounts/anne")), Requirement.ACCOUNT_OWNER);
  }
}
