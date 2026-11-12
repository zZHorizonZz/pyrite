package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;
import static dev.horizon.pyrite.PyriteFixture.CAROL;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectPlaintext;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.KeepCiphertext;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A decrypter only decrypts, and an encrypter only encrypts.")
final class KeyRelationSeparation extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", "a"));
    call(new KeepCiphertext(Value.OLD_CIPHERTEXT));
    call(new GrantRelation(ANNE, "keys/payments", "decrypter", BOB));
    call(new GrantRelation(ANNE, "keys/payments", "encrypter", CAROL));
    callAndContinueOnFailure(new Decrypt(BOB, "keys/payments", Value.OLD_CIPHERTEXT, "a"), Requirement.KEY_PERMISSION);
    callAndContinueOnFailure(new ExpectPlaintext("card"), Requirement.KEY_PERMISSION);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Encrypt(BOB, "keys/payments", "x", "a")),
        Requirement.KEY_PERMISSION);
    callAndContinueOnFailure(new Encrypt(CAROL, "keys/payments", "x", "a"), Requirement.KEY_PERMISSION);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new Decrypt(CAROL, "keys/payments", Value.OLD_CIPHERTEXT,
            "a")),
        Requirement.KEY_PERMISSION);
  }
}
