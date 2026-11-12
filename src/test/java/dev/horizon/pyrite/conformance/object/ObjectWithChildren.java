package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.DeleteObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A resource with resources under it is not forgotten.")
final class ObjectWithChildren extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new DeleteObject(ANNE, "organizations/acme")),
        Requirement.OBJECT_DELETE);
    callAndStopOnFailure(new DeleteObject(ANNE, "projects/ledger"), Requirement.OBJECT_DELETE);
    callAndStopOnFailure(new DeleteObject(ANNE, "organizations/acme"), Requirement.OBJECT_DELETE);
  }
}
