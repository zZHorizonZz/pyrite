package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectChanges;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectObjects;
import dev.horizon.pyrite.conformance.condition.ExpectSubjects;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.ListChanges;
import dev.horizon.pyrite.conformance.condition.ListObjects;
import dev.horizon.pyrite.conformance.condition.ListSubjects;
import java.util.List;

@Summary("A resource named as a relative resource name, up to the length of an OpenFGA object, comes back as it was"
    + " named.")
final class ObjectNameKept extends AccessModule {

  private static final String ACME = "acme.dev/Organization:organizations/acme.dev";
  private static final String LEDGER = "acme.dev/Project:organizations/acme.dev/projects/ledger_2026-Q1.v2";
  private static final String NESTED = "acme.dev/Project:organizations/acme.dev/folders/"
      + "0f8fad5b-d9cb-469f-a165-70867728950e/projects/9b2c5c3e-1d4f-4c7a-8b0e-2a6f3c1d7e55";
  private static final String ZURICH = "acme.dev/Project:organizations/acme.dev/projects/zürich@acme";
  private static final String LONGEST = "acme.dev/Project:" + "p".repeat(256 - "acme.dev/Project:".length());
  private static final String SUBJECT = "pyrite.dev/Account:https%3A%2F%2Fissuer.example%2F" + "a".repeat(200);

  @Override
  protected void run() {
    call(new CreateObject(ANNE, ACME, ""));
    for (String name : List.of(LEDGER, NESTED, ZURICH, LONGEST)) {
      callAndStopOnFailure(new CreateObject(ANNE, name, ACME), Requirement.OBJECT_NAME_KEPT);
    }
    call(new GrantRelation(ANNE, LEDGER, "viewer", BOB));
    call(new GrantRelation(ANNE, NESTED, "viewer", SUBJECT));
    call(new ListObjects(ANNE, "acme.dev/Project", "can_view"));
    callAndContinueOnFailure(new ExpectObjects(List.of(NESTED, LEDGER, ZURICH, LONGEST).stream().sorted().toList()),
        Requirement.OBJECT_NAME_KEPT);
    call(new ListObjects(SUBJECT, "acme.dev/Project", "can_view"));
    callAndContinueOnFailure(new ExpectObjects(List.of(NESTED)), Requirement.OBJECT_NAME_KEPT);
    call(new ListSubjects(ANNE, NESTED, "can_view", "pyrite.dev/Account"));
    callAndContinueOnFailure(new ExpectSubjects(List.of("pyrite.dev/Account:accounts/anne", SUBJECT)),
        Requirement.OBJECT_NAME_KEPT);
    call(new Check(ANNE, SUBJECT, NESTED, "can_view"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.OBJECT_NAME_KEPT);
    call(new ListChanges(ANNE, "acme.dev/Project"));
    callAndContinueOnFailure(new ExpectChanges(List.of(LEDGER + " owner " + ANNE, NESTED + " owner " + ANNE,
        ZURICH + " owner " + ANNE, LONGEST + " owner " + ANNE, LEDGER + " viewer " + BOB, NESTED + " viewer "
            + SUBJECT)
        .stream().map(change -> change + " GRANTED").toList()), Requirement.OBJECT_NAME_KEPT);
  }
}
