package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectEncryptionVersion;
import dev.horizon.pyrite.conformance.condition.ExpectPlaintext;
import dev.horizon.pyrite.conformance.condition.ExpectPlaintextSecrecy;

@Summary("A key decrypts what it encrypted, and names the version.")
final class EncryptionRoundTrip extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new Encrypt(ANNE, "keys/payments", "4111 1111 1111 1111", "orders/1042"), Requirement.KEY_ENCRYPTION);
    callAndContinueOnFailure(new ExpectEncryptionVersion("keyVersions/payments-1"), Requirement.KEY_ENCRYPTION);
    callAndContinueOnFailure(new ExpectPlaintextSecrecy("4111"), Requirement.KEY_ENCRYPTION);
    callAndStopOnFailure(new Decrypt(ANNE, "keys/payments", Value.CIPHERTEXT, "orders/1042"), Requirement.KEY_ENCRYPTION);
    callAndStopOnFailure(new ExpectPlaintext("4111 1111 1111 1111"), Requirement.KEY_ENCRYPTION);
  }
}
