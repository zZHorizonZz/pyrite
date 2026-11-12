package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectByteFlipRefusal;

@Summary("A ciphertext with any byte changed is refused.")
final class ByteFlip extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", "orders/1042"));
    callAndStopOnFailure(new ExpectByteFlipRefusal("keys/payments", "orders/1042"), Requirement.KEY_TAMPERING);
  }
}
