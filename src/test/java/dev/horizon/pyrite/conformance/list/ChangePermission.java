package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectChanges;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListChanges;
import java.util.List;

@Summary("A change is listed only to who may grant on its resource.")
final class ChangePermission extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "viewer", BOB));
    call(new ListChanges(BOB, "acme.dev/Project"));
    callAndContinueOnFailure(new ExpectChanges(List.of()), Requirement.CHANGE_LIST);
    call(new ListChanges(ANNE, "acme.dev/Project"));
    callAndContinueOnFailure(
        new ExpectChanges(List.of("acme.dev/Project:projects/ledger owner pyrite.dev/Account:accounts/anne GRANTED",
            "acme.dev/Project:projects/ledger viewer pyrite.dev/Account:accounts/bob GRANTED")),
        Requirement.CHANGE_LIST);
  }
}
