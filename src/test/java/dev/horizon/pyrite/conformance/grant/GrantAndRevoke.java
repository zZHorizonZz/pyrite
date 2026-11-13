package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectGrant;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.Revoke;

@Summary("A grant gives the relation, and a revoke takes it back.")
final class GrantAndRevoke extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "projects/ledger", "viewer", BOB));
    callAndContinueOnFailure(new ExpectGrant("projects/ledger", "viewer", BOB), Requirement.GRANT_WRITE);
    call(new Check(BOB, "projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.GRANT_WRITE);
    call(new Revoke(ANNE, "projects/ledger", "viewer", BOB));
    call(new Check(BOB, "projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.GRANT_REVOKE);
  }
}
