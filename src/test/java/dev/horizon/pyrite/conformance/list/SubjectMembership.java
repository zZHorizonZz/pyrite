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
    call(new GrantRelation(ANNE, "acme.dev/Organization:organizations/acme", "member", BOB));
    call(new GrantRelation(ANNE, "acme.dev/Group:groups/finance", "member", CAROL));
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "viewer", "acme.dev/Group:groups/finance#member"));
    call(new ListSubjects(ANNE, "acme.dev/Project:projects/ledger", "can_view", "pyrite.dev/Account"));
    callAndContinueOnFailure(new ExpectSubjects(List.of(ANNE, BOB, CAROL)), Requirement.SUBJECT_LIST);
    call(new ListSubjects(ANNE, "acme.dev/Project:projects/ledger", "viewer", "acme.dev/Group#member"));
    callAndContinueOnFailure(new ExpectSubjects(List.of("acme.dev/Group:groups/finance#member")), Requirement.SUBJECT_LIST);
  }
}
