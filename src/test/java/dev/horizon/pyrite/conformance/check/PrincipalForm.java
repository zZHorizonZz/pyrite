package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A principal is of the form type:id, with names that OpenFGA takes.")
final class PrincipalForm extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    for (String principal : List.of("bob", "accounts/bob", "pyrite.dev/Account:", "pyrite.dev/Account:*",
        "pyrite.dev/Account:accounts/bob#member", "pyrite.dev/Account:accounts/bob:v2", "pyrite.dev/Account:accounts/b ob",
        "pyrite.dev/Account:accounts/bob\n", "pyrite@dev/Account:accounts/bob", "t".repeat(255) + ":accounts/bob",
        "pyrite.dev/Account:" + "b".repeat(512))) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new Check(ANNE, principal,
          "acme.dev/Project:projects/ledger", "can_view")), Requirement.ACCESS_PRINCIPAL);
    }
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new Check("pyrite.dev/Account:accounts/a"
        + "n".repeat(512), "acme.dev/Project:projects/ledger", "can_view")), Requirement.ACCESS_PRINCIPAL);
  }
}
