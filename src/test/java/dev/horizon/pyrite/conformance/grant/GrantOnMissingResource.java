package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A grant on a missing resource is not found.")
final class GrantOnMissingResource extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new GrantRelation(ANNE, "projects/payroll", "viewer", BOB)),
        Requirement.OBJECT_MISSING);
  }
}
