package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectObjects;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListObjects;
import java.util.List;

@Summary("The objects of a type are those the subject has the permission on, through its parents too.")
final class ObjectList extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new CreateObject(ANNE, "projects/payroll", "organizations/acme"));
    call(new CreateObject(BOB, "organizations/globex", ""));
    call(new CreateObject(BOB, "projects/mars", "organizations/globex"));
    call(new GrantRelation(BOB, "projects/mars", "viewer", ANNE));
    call(new ListObjects(ANNE, "acme.dev/Project", "can_view"));
    callAndContinueOnFailure(new ExpectObjects(List.of("projects/ledger", "projects/mars", "projects/payroll")),
        Requirement.OBJECT_LIST);
    call(new ListObjects(BOB, "acme.dev/Project", "can_view"));
    callAndContinueOnFailure(new ExpectObjects(List.of("projects/mars")), Requirement.OBJECT_LIST);
  }
}
