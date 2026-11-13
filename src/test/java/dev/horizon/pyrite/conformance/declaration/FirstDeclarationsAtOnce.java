package dev.horizon.pyrite.conformance.declaration;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectOneStore;
import dev.horizon.pyrite.conformance.condition.ForgetDeclarations;
import dev.horizon.pyrite.conformance.condition.WriteDeclarationsAtOnce;

@Summary("Declarations written at once before any are each stored, one on the other, with one store of grants.")
final class FirstDeclarationsAtOnce extends ConformanceModule {

  @Override
  protected void run() {
    call(new ForgetDeclarations());
    callAndStopOnFailure(new WriteDeclarationsAtOnce(3), Requirement.DECLARATION_FIRST);
    callAndStopOnFailure(new ExpectOneStore(3), Requirement.DECLARATION_FIRST);
  }
}
