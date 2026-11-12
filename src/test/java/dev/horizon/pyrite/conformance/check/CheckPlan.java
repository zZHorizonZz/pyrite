package dev.horizon.pyrite.conformance.check;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of checks: who has a permission on a resource, and why.
 */
public final class CheckPlan implements Plan {

  @Override
  public String name() {
    return "checks";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(SelfCheck::new, CheckOfOtherPrincipal::new, PrincipalForm::new,
        GrantInheritance::new, ParentBlock::new, ConditionHolding::new, ConditionValueOverride::new, CheckRelation::new,
        CheckOfMissingResource::new);
  }
}
