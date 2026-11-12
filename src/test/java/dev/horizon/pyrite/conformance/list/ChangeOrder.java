package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectChanges;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListChanges;
import dev.horizon.pyrite.conformance.condition.Revoke;
import java.util.List;

@Summary("Changes are listed oldest first.")
final class ChangeOrder extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "projects/ledger", "viewer", BOB));
    call(new Revoke(ANNE, "projects/ledger", "viewer", BOB));
    call(new ListChanges(ANNE, "acme.dev/Project"));
    callAndStopOnFailure(new ExpectChanges(List.of("owner user:anne GRANTED", "viewer user:bob GRANTED",
        "viewer user:bob REVOKED")), Requirement.CHANGE_LIST);
  }
}
