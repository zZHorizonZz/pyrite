package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectEncryptionVersion;
import dev.horizon.pyrite.conformance.condition.ExpectKeyVersions;
import dev.horizon.pyrite.conformance.condition.ExpectPlaintext;
import dev.horizon.pyrite.conformance.condition.ExpectPrimaryVersion;
import dev.horizon.pyrite.conformance.condition.GetKey;
import dev.horizon.pyrite.conformance.condition.KeepCiphertext;
import dev.horizon.pyrite.conformance.condition.ListKeyVersions;
import dev.horizon.pyrite.conformance.condition.RotateKey;
import java.util.List;

@Summary("A rotation makes the next version primary and keeps the old one readable.")
final class KeyRotation extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    call(new KeepCiphertext(Value.OLD_CIPHERTEXT));
    callAndStopOnFailure(new RotateKey(ANNE, "keys/payments"), Requirement.KEY_ROTATION);
    callAndContinueOnFailure(new ExpectPrimaryVersion("keyVersions/payments-2"), Requirement.KEY_ROTATION);
    call(new GetKey(ANNE, "keys/payments", Value.KEY));
    callAndContinueOnFailure(new ExpectPrimaryVersion("keyVersions/payments-2"), Requirement.KEY_ROTATION);
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    callAndContinueOnFailure(new ExpectEncryptionVersion("keyVersions/payments-2"), Requirement.KEY_ROTATION);
    callAndContinueOnFailure(new Decrypt(ANNE, "keys/payments", Value.OLD_CIPHERTEXT, ""), Requirement.KEY_ROTATION);
    callAndContinueOnFailure(new ExpectPlaintext("card"), Requirement.KEY_ROTATION);
    call(new ListKeyVersions(ANNE, "keys/payments"));
    callAndContinueOnFailure(new ExpectKeyVersions(List.of("keyVersions/payments-1", "keyVersions/payments-2")),
        Requirement.KEY_ROTATION);
  }
}
