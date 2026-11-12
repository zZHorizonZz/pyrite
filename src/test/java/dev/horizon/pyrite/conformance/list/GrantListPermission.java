package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListGrants;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Listing grants needs the grant permission, even for a grantee.")
final class GrantListPermission extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "projects/ledger", "editor", BOB));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new ListGrants(BOB, "projects/ledger")),
        Requirement.GRANT_LIST);
  }
}
