package dev.horizon.pyrite.conformance.grant;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of grants: how relations are given and taken back.
 */
public final class GrantPlan implements Plan {

  @Override
  public String name() {
    return "grants";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(GrantAndRevoke::new, GrantRepetition::new, WildcardGrant::new,
        GroupMembership::new,
        GroupCycle::new, GrantPermission::new, GrantableRelation::new, GranteeType::new, GrantOnMissingResource::new,
        ConditionOfRelation::new, ConditionDeclaration::new, ConditionParameter::new);
  }
}
