package dev.horizon.pyrite.conformance.declaration;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of declarations: how they are written and read.
 */
public final class DeclarationPlan implements Plan {

  @Override
  public String name() {
    return "declarations";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(LatestDeclaration::new, EarlierDeclaration::new, DeclarationRefusal::new,
        DeclarationWithoutAccount::new, UnknownDeclaration::new, NoDeclaration::new);
  }
}
