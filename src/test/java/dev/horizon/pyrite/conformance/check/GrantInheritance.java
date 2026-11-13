package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantRelation;

@Summary("A grant on the organization reaches its projects.")
final class GrantInheritance extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantRelation(ANNE, "organizations/acme", "admin", BOB));
    call(new Check(ANNE, BOB, "projects/ledger", "can_edit"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.ACCESS_HIERARCHY);
    call(new Check(ANNE, BOB, "projects/ledger", "owner"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.ACCESS_HIERARCHY);
  }
}
