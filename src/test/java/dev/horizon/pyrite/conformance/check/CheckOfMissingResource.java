package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A check on a missing resource is not found.")
final class CheckOfMissingResource extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new Check(ANNE, "projects/payroll", "can_view")),
        Requirement.OBJECT_MISSING);
  }
}
