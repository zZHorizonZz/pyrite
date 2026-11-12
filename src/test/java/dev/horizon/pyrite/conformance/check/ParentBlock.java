package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantRelation;

@Summary("A block on the organization overrides a grant on the project.")
final class ParentBlock extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantRelation(ANNE, "projects/ledger", "editor", BOB));
    call(new GrantRelation(ANNE, "organizations/acme", "blocked", BOB));
    call(new Check(ANNE, BOB, "projects/ledger", "can_edit"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.ACCESS_HIERARCHY);
    call(new Check(ANNE, BOB, "projects/ledger", "editor"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.ACCESS_HIERARCHY);
  }
}
