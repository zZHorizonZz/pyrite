package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectVersionRenameRefusal;
import dev.horizon.pyrite.conformance.condition.RotateKey;

@Summary("A ciphertext that names another version of its key is refused.")
final class VersionRename extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", "orders/1042"));
    call(new RotateKey(ANNE, "keys/payments"));
    callAndStopOnFailure(new ExpectVersionRenameRefusal("keys/payments", "orders/1042", "payments-1", "payments-2"),
        Requirement.KEY_TAMPERING);
  }
}
