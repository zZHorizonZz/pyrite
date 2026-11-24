package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.CAROL;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.Revoke;

@Summary("A group grants what is granted to its members, through nested groups.")
final class GroupMembership extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    call(new GrantRelation(ANNE, "acme.dev/Group:groups/audit", "member", CAROL));
    call(new GrantRelation(ANNE, "acme.dev/Group:groups/finance", "member", "acme.dev/Group:groups/audit#member"));
    call(new GrantRelation(ANNE, "acme.dev/Project:projects/ledger", "viewer", "acme.dev/Group:groups/finance#member"));
    call(new Check(CAROL, "acme.dev/Project:projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.GRANT_GROUP);
    call(new Revoke(ANNE, "acme.dev/Group:groups/audit", "member", CAROL));
    call(new Check(CAROL, "acme.dev/Project:projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.GRANT_GROUP);
  }
}
