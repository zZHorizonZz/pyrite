package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.DeleteKeyVersion;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.KeepCiphertext;
import dev.horizon.pyrite.conformance.condition.RotateKey;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A destroyed version no longer decrypts.")
final class VersionDestruction extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    call(new KeepCiphertext(Value.OLD_CIPHERTEXT));
    call(new RotateKey(ANNE, "keys/payments"));
    call(new DeleteKeyVersion("keyVersions/payments-1"));
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new Decrypt(ANNE, "keys/payments", Value.OLD_CIPHERTEXT, "")),
        Requirement.KEY_VERSION_STATE);
  }
}
