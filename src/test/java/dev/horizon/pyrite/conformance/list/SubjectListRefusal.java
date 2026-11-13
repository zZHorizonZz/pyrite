package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListSubjects;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Listing subjects needs a type and the grant permission.")
final class SubjectListRefusal extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "projects/ledger", "viewer", BOB));
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new ListSubjects(ANNE, "projects/ledger", "can_view", "")),
        Requirement.SUBJECT_LIST);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new ListSubjects(BOB, "projects/ledger", "can_view",
        "user")), Requirement.SUBJECT_LIST);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new ListSubjects(ANNE, "projects/ledger", "flies", "user")),
        Requirement.SUBJECT_LIST);
  }
}
