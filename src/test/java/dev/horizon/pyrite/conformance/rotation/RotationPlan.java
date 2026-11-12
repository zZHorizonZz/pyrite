package dev.horizon.pyrite.conformance.rotation;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of rotation: versions, schedules, and states.
 */
public final class RotationPlan implements Plan {

  @Override
  public String name() {
    return "rotation";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(KeyRotation::new, ConcurrentRotation::new, OldSigningVersion::new,
        RotationPermission::new,
        RotationPeriod::new, PeriodChange::new, NoRotationPeriod::new, VersionDisabling::new, VersionDestruction::new,
        VerificationOfDisabledVersion::new, PrimaryVersionState::new, VersionStateValue::new);
  }
}
