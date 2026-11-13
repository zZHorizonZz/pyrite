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
    for (String grantee : List.of("user:*", "project:payroll", "group:finance", "group:finance#manager",
        "folder:docs", "bob", "", "user:bob#member")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new GrantRelation(ANNE, "projects/ledger", "owner",
          grantee)), Requirement.GRANT_GRANTEE);
    }
  }
}
