package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A check names a relation of the type.")
final class CheckRelation extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new Check(ANNE, BOB, "projects/ledger", "flies")),
        Requirement.ACCESS_CHECK);
  }
}
