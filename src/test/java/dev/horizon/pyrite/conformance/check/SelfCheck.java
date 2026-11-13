package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantRelation;

@Summary("A subject checks itself without the grant permission.")
final class SelfCheck extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantRelation(ANNE, "organizations/acme", "member", BOB));
    call(new Check(BOB, "projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.ACCESS_CHECK);
    call(new Check(BOB, "projects/ledger", "can_edit"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.ACCESS_CHECK);
  }
}
