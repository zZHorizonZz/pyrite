package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.DeleteObject;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A forgotten resource keeps no grant, and its name can be made known again.")
final class ObjectForgetting extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantRelation(ANNE, "projects/ledger", "viewer", BOB));
    callAndStopOnFailure(new DeleteObject(ANNE, "projects/ledger"), Requirement.OBJECT_DELETE);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new Check(BOB, "projects/ledger", "can_view")),
        Requirement.OBJECT_DELETE);
    callAndStopOnFailure(new CreateObject(ANNE, "projects/ledger", "organizations/acme"), Requirement.OBJECT_DELETE);
    call(new Check(BOB, "projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.OBJECT_DELETE);
  }
}
