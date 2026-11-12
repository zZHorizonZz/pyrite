package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectSubjects;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListSubjects;
import java.util.List;

@Summary("Subjects include the members of groups and of ancestors.")
final class SubjectMembership extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "organizations/acme", "member", BOB));
    call(new GrantRelation(ANNE, "groups/finance", "member", CAROL));
    call(new GrantRelation(ANNE, "projects/ledger", "viewer", "group:finance#member"));
    call(new ListSubjects(ANNE, "projects/ledger", "can_view", "user"));
    callAndContinueOnFailure(new ExpectSubjects(List.of(ANNE, BOB, CAROL)), Requirement.SUBJECT_LIST);
    call(new ListSubjects(ANNE, "projects/ledger", "viewer", "group#member"));
    callAndContinueOnFailure(new ExpectSubjects(List.of("group:finance#member")), Requirement.SUBJECT_LIST);
  }
}
