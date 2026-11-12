package dev.horizon.pyrite.conformance.identifier;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of identifiers and accounts: their formats, owners, and uniqueness.
 */
public final class IdentifierPlan implements Plan {

  @Override
  public String name() {
    return "identifiers";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(IdentifierNormalization::new, IdentifierMatch::new, IdentifierKind::new,
        IdentifierWithoutKind::new, IdentifierOwner::new, IdentifierUniqueness::new, IdentifierList::new, AccountKind::new,
        AccountId::new, AccountOwner::new);
  }
}
