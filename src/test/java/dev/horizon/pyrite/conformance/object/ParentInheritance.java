package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantRelation;

@Summary("A resource under a parent inherits what is granted on the parent.")
final class ParentInheritance extends AccessModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    call(new GrantRelation(ANNE, "organizations/acme", "member", BOB));
    callAndStopOnFailure(new CreateObject(ANNE, "projects/ledger", "organizations/acme"), Requirement.OBJECT_CREATE);
    call(new Check(BOB, "projects/ledger", "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.OBJECT_CREATE);
    call(new Check(ANNE, "projects/ledger", "owner"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.OBJECT_CREATE);
  }
}
