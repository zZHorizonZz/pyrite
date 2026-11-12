package dev.horizon.pyrite.conformance.rotation;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectConcurrentRotations;

@Summary("Rotations at the same time each make a version of their own.")
final class ConcurrentRotation extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectConcurrentRotations("keys/payments", 3), Requirement.KEY_ROTATION_RACE);
  }
}
