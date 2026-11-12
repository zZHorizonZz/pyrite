package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A parent is known and of one of the type's parent types.")
final class ObjectParent extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new CreateObject(ANNE, "projects/payroll",
        "organizations/globex")), Requirement.OBJECT_PARENT);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "projects/payroll", "projects/ledger")),
        Requirement.OBJECT_PARENT);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "projects/payroll", "")),
        Requirement.OBJECT_PARENT);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "organizations/globex", "organizations/acme")),
        Requirement.OBJECT_PARENT);
  }
}
