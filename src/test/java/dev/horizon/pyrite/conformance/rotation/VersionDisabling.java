package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Decrypt;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectPlaintext;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.KeepCiphertext;
import dev.horizon.pyrite.conformance.condition.RotateKey;
import dev.horizon.pyrite.conformance.condition.SetVersionState;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A disabled version no longer decrypts until it is enabled again.")
final class VersionDisabling extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    call(new KeepCiphertext(Value.OLD_CIPHERTEXT));
    call(new RotateKey(ANNE, "keys/payments"));
    call(new SetVersionState("keyVersions/payments-1", 1, KeyVersion.State.DISABLED));
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new Decrypt(ANNE, "keys/payments", Value.OLD_CIPHERTEXT, "")),
        Requirement.KEY_VERSION_STATE);
    call(new SetVersionState("keyVersions/payments-1", 2, KeyVersion.State.ENABLED));
    callAndStopOnFailure(new Decrypt(ANNE, "keys/payments", Value.OLD_CIPHERTEXT, ""), Requirement.KEY_VERSION_STATE);
    callAndStopOnFailure(new ExpectPlaintext("card"), Requirement.KEY_VERSION_STATE);
  }
}
