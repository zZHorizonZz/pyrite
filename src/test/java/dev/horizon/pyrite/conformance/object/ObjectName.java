package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A name is of a declared type, with an id that OpenFGA takes.")
final class ObjectName extends AccessModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "folders/docs", "")),
        Requirement.OBJECT_NAME);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "organizations/Acme Corp", "")),
        Requirement.OBJECT_NAME);
  }
}
