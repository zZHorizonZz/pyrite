package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Creating under a parent needs the type's create permission on it.")
final class CreatePermission extends AccessModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new CreateObject(BOB, "projects/ledger", "organizations/acme")),
        Requirement.OBJECT_PERMISSION);
  }
}
