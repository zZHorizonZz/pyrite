package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectGrants;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListGrants;
import java.util.List;

@Summary("Grants are listed by relation and by grantee.")
final class GrantFilter extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "viewer", BOB));
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "viewer", CAROL));
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "editor", BOB));
    call(new ListGrants(ANNE, "acme.dev/Project:projects/ledger", "viewer", ""));
    callAndContinueOnFailure(
        new ExpectGrants(List.of("viewer pyrite.dev/Account:accounts/bob", "viewer pyrite.dev/Account:accounts/carol")),
        Requirement.GRANT_LIST);
    call(new ListGrants(ANNE, "acme.dev/Project:projects/ledger", "", BOB));
    callAndContinueOnFailure(
        new ExpectGrants(List.of("editor pyrite.dev/Account:accounts/bob", "viewer pyrite.dev/Account:accounts/bob")),
        Requirement.GRANT_LIST);
  }
}
