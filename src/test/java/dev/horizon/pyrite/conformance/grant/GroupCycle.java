package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A grant that makes a cycle of groups is refused.")
final class GroupCycle extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "groups/finance", "member", "group:audit#member"));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new GrantRelation(ANNE, "groups/audit", "member",
        "group:finance#member")), Requirement.GRANT_CYCLE);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new GrantRelation(ANNE, "groups/finance", "member",
            "group:finance#member")),
        Requirement.GRANT_CYCLE);
  }
}
