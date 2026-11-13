package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectTruncationRefusal;

@Summary("A ciphertext cut short anywhere is refused.")
final class CiphertextTruncation extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", "orders/1042"));
    callAndStopOnFailure(new ExpectTruncationRefusal("keys/payments", "orders/1042"), Requirement.KEY_TAMPERING);
  }
}
