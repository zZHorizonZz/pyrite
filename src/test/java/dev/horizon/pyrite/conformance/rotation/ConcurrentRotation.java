package dev.horizon.pyrite.conformance.rotation;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRotationConflicts;

@Summary("Of rotations at the same time, each that succeeds makes a version of its own, and each that another change"
    + " came first to fails with ABORTED and leaves no version.")
final class ConcurrentRotation extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectRotationConflicts("keys/payments", 3), Requirement.KEY_ROTATION_CONFLICT);
  }
}
