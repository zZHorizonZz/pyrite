package dev.horizon.pyrite.conformance.declaration;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.DeclarationChange;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.ExpectLatestDeclaration;
import dev.horizon.pyrite.conformance.condition.MakeDeclarationOlder;
import dev.horizon.pyrite.conformance.condition.ReadDeclaration;
import dev.horizon.pyrite.conformance.condition.WriteDeclaration;
import java.time.Duration;

@Summary("The latest declaration is the one that every service reads.")
final class LatestDeclaration extends ConformanceModule {

  @Override
  protected void run() {
    call(new ReadDeclaration(""));
    call(new MakeDeclarationOlder(Duration.ofSeconds(1)));
    callAndStopOnFailure(new WriteDeclaration(DeclarationChange.EDITOR_CREATES), Requirement.DECLARATION_LATEST);
    callAndStopOnFailure(new ExpectLatestDeclaration(true), Requirement.DECLARATION_LATEST);
    call(new CreateObject(ANNE, "organizations/acme", ""));
    call(new CreateObject(ANNE, "projects/ledger", "organizations/acme"));
    call(new Check(ANNE, "projects/ledger", "editor"));
    callAndContinueOnFailure(new ExpectCheckAnswer(true), Requirement.DECLARATION_LATEST);
    call(new Check(ANNE, "projects/ledger", "owner"));
    callAndContinueOnFailure(new ExpectCheckAnswer(false), Requirement.DECLARATION_LATEST);
  }
}
