package dev.horizon.pyrite.conformance.encryption;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectForeignBytesRefusal;

@Summary("Bytes that no key made are refused.")
final class ForeignByteRefusal extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectForeignBytesRefusal("keys/payments"), Requirement.KEY_TAMPERING);
  }
}
