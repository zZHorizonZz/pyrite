package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantRelation;

@Summary("A wildcard grants every user.")
final class WildcardGrant extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "viewer", "pyrite.dev/Account:*"));
    call(new Check("pyrite.dev/Account:accounts/anyone", "acme.dev/Project:projects/ledger", "can_view"));
    callAndStopOnFailure(new ExpectCheckAnswer(true), Requirement.GRANT_WRITE);
  }
}
