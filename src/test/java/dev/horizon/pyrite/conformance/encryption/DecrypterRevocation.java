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
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.Revoke;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A revoked decrypter no longer decrypts.")
final class DecrypterRevocation extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", "a"));
    call(new GrantRelation(ANNE, "keys/payments", "decrypter", BOB));
    call(new Revoke(ANNE, "keys/payments", "decrypter", BOB));
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Decrypt(BOB, "keys/payments", Value.CIPHERTEXT, "a")),
        Requirement.KEY_PERMISSION);
  }
}
