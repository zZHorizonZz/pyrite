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
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new CreateObject(ANNE, "acme.dev/Project:projects/payroll",
        "acme.dev/Organization:organizations/globex")), Requirement.OBJECT_PARENT);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT,
            new CreateObject(ANNE, "acme.dev/Project:projects/payroll", "acme.dev/Project:projects/ledger")),
        Requirement.OBJECT_PARENT);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "acme.dev/Project:projects/payroll", "")),
        Requirement.OBJECT_PARENT);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT,
            new CreateObject(ANNE, "acme.dev/Organization:organizations/globex", "acme.dev/Organization:organizations/acme")),
        Requirement.OBJECT_PARENT);
  }
}
