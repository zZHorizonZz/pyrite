package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A ciphertext decrypts only with the key that made it.")
final class CiphertextOfOtherKey extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new CreateKey("organizations/acme", "billing", Algorithm.AES256_GCM));
    call(new Encrypt(ANNE, "keys/payments", "card", "a"));
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new Decrypt(ANNE, "keys/billing", Value.CIPHERTEXT, "a")),
        Requirement.KEY_TAMPERING);
  }
}
