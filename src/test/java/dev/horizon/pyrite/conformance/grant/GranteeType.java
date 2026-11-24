package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A grantee is of a type that the relation takes.")
final class GranteeType extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    for (String grantee : List.of("pyrite.dev/Account:*", "acme.dev/Project:projects/payroll", "acme.dev/Group:groups/finance",
        "acme.dev/Group:groups/finance#manager",
        "folder:docs", "bob", "", "pyrite.dev/Account:accounts/bob#member",
        "acme.dev/Group:groups/finance#", "acme.dev/Group:groups/finance#" + "m".repeat(51),
        "acme.dev/Group:groups/finance#mem ber", "acme.dev/Group:groups/finance:v2#member",
        "pyrite.dev/Account:accounts/bob carol", "pyrite.dev/Account:" + "b".repeat(512))) {
      callAndContinueOnFailure(
          new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "owner",
              grantee)),
          Requirement.GRANT_GRANTEE);
    }
  }
}
