package dev.horizon.pyrite.conformance.encryption;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRandomEncryption;

@Summary("Every encryption of the same plaintext is different.")
final class RandomEncryption extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectRandomEncryption("keys/payments", 50), Requirement.KEY_ENCRYPTION);
  }
}
