package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.Revoke;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Granting and revoking need the type's grant permission.")
final class GrantPermission extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "projects/ledger", "editor", BOB));
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new GrantRelation(BOB, "projects/ledger", "viewer", CAROL)),
        Requirement.GRANT_PERMISSION);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Revoke(BOB, "projects/ledger", "editor", BOB)),
        Requirement.GRANT_PERMISSION);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new GrantRelation(BOB, "projects/ledger", "owner", BOB)),
        Requirement.GRANT_PERMISSION);
  }
}
