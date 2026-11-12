package dev.horizon.pyrite.conformance.identifier;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("An account's kind is HUMAN or SERVICE.")
final class AccountKind extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (String kind : List.of("", "PERSON", "human", "ROBOT")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateAccountOfKind("bob", kind)),
          Requirement.ACCOUNT_KIND);
    }
  }
}
