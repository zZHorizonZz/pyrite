package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.ListObjects;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A permission is a relation of the type.")
final class ObjectListPermission extends AccessModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new ListObjects(ANNE, "acme.dev/Project", "can_fly")),
        Requirement.OBJECT_LIST);
  }
}
