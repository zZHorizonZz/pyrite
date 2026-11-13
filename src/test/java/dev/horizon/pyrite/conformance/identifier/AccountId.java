package dev.horizon.pyrite.conformance.identifier;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An account's id is taken once.")
final class AccountId extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.ALREADY_EXISTS, new CreateAccountOfKind("anne", "HUMAN")),
        Requirement.ACCOUNT_ID);
  }
}
