package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A principal without a relation on the key neither encrypts nor decrypts.")
final class NoKeyRelation extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", "a"));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Encrypt(BOB, "keys/payments", "x", "a")),
        Requirement.KEY_PERMISSION);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Decrypt(BOB, "keys/payments", Value.CIPHERTEXT, "a")),
        Requirement.KEY_PERMISSION);
  }
}
