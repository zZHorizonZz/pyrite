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
    call(new CreateObject(ANNE, "acme.dev/Project:projects/payroll", "acme.dev/Organization:organizations/acme"));
    call(new CreateObject(BOB, "acme.dev/Organization:organizations/globex", ""));
    call(new CreateObject(BOB, "acme.dev/Project:projects/mars", "acme.dev/Organization:organizations/globex"));
    call(new GrantRelation(BOB, "acme.dev/Project:projects/mars", "viewer", ANNE));
    call(new ListObjects(ANNE, "acme.dev/Project", "can_view"));
    callAndContinueOnFailure(
        new ExpectObjects(
            List.of("acme.dev/Project:projects/ledger", "acme.dev/Project:projects/mars", "acme.dev/Project:projects/payroll")),
        Requirement.OBJECT_LIST);
    call(new ListObjects(BOB, "acme.dev/Project", "can_view"));
    callAndContinueOnFailure(new ExpectObjects(List.of("acme.dev/Project:projects/mars")), Requirement.OBJECT_LIST);
  }
}
