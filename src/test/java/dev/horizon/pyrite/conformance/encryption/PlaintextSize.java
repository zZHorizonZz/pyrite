package dev.horizon.pyrite.conformance.encryption;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRoundTrips;

@Summary("Empty, binary and large plaintexts survive the round trip.")
final class PlaintextSize extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectRoundTrips("keys/payments"), Requirement.KEY_ENCRYPTION);
  }
}
