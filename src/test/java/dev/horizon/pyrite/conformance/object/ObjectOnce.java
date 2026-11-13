package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A resource is made known once.")
final class ObjectOnce extends AccessModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ALREADY_EXISTS, new CreateObject(BOB, "organizations/acme", "")),
        Requirement.OBJECT_CREATE);
    call(new Check(BOB, "organizations/acme", "admin"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.OBJECT_CREATE);
  }
}
