package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.DeleteObject;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Forgetting a resource needs the type's grant permission on it.")
final class DeletePermission extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantRelation(ANNE, "projects/ledger", "editor", BOB));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new DeleteObject(BOB, "projects/ledger")),
        Requirement.OBJECT_DELETE);
    call(new Check(BOB, "projects/ledger", "editor"));
    callAndStopOnFailure(new ExpectCheckAnswer(true), Requirement.OBJECT_DELETE);
  }
}
