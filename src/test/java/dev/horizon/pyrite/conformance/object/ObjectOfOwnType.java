package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("CreateObject makes no account or key, which their own services create.")
final class ObjectOfOwnType extends AccessModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "acme.dev/Organization:organizations/acme", ""));
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "pyrite.dev/Account:accounts/squat",
            "")),
        Requirement.OBJECT_OWN_TYPES);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, "pyrite.dev/Key:keys/squat",
        "acme.dev/Organization:organizations/acme")), Requirement.OBJECT_OWN_TYPES);
  }
}
