package dev.horizon.pyrite.conformance.declaration;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.DeclarationChange;
import dev.horizon.pyrite.conformance.condition.ExpectEarlierDeclaration;
import dev.horizon.pyrite.conformance.condition.MakeDeclarationOlder;
import dev.horizon.pyrite.conformance.condition.ReadDeclaration;
import dev.horizon.pyrite.conformance.condition.WriteDeclaration;
import java.time.Duration;

@Summary("An earlier declaration is read back by its id, as it was written.")
final class EarlierDeclaration extends ConformanceModule {

  @Override
  protected void run() {
    call(new ReadDeclaration(""));
    call(new MakeDeclarationOlder(Duration.ofSeconds(1)));
    call(new ReadDeclaration(""));
    call(new WriteDeclaration(DeclarationChange.EDITOR_CREATES));
    callAndStopOnFailure(new ExpectEarlierDeclaration(), Requirement.DECLARATION_READ);
  }
}
