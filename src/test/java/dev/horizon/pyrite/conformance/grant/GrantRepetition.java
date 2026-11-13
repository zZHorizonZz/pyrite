package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectGrants;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListGrants;
import dev.horizon.pyrite.conformance.condition.Revoke;
import java.util.List;

@Summary("Granting twice and revoking twice change nothing.")
final class GrantRepetition extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "projects/ledger", "viewer", BOB));
    callAndContinueOnFailure(new GrantRelation(ANNE, "projects/ledger", "viewer", BOB), Requirement.GRANT_WRITE);
    call(new ListGrants(ANNE, "projects/ledger"));
    callAndContinueOnFailure(new ExpectGrants(List.of("owner user:anne", "viewer user:bob")), Requirement.GRANT_WRITE);
    call(new Revoke(ANNE, "projects/ledger", "viewer", BOB));
    callAndContinueOnFailure(new Revoke(ANNE, "projects/ledger", "viewer", BOB), Requirement.GRANT_REVOKE);
    call(new ListGrants(ANNE, "projects/ledger"));
    callAndContinueOnFailure(new ExpectGrants(List.of("owner user:anne")), Requirement.GRANT_REVOKE);
  }
}
