package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectSubjects;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListSubjects;
import java.util.List;

@Summary("A blocked principal is not a subject.")
final class SubjectBlock extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "acme.dev/Organization:organizations/acme", "member", BOB));
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "blocked", BOB));
    call(new ListSubjects(ANNE, "acme.dev/Project:projects/ledger", "can_view", "pyrite.dev/Account"));
    callAndStopOnFailure(new ExpectSubjects(List.of(ANNE)), Requirement.SUBJECT_LIST);
  }
}
