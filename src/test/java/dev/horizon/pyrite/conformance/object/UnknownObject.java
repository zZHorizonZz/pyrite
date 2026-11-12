package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.DeleteObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An unknown resource is not found.")
final class UnknownObject extends AccessModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new DeleteObject(ANNE, "projects/missing")),
        Requirement.OBJECT_MISSING);
  }
}
