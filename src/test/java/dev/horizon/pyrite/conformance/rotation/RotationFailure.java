package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectEncryptionVersion;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.MakeResourceOlder;
import dev.horizon.pyrite.conformance.condition.RenameTable;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A scheduled rotation that fails is due again at the next use, not a period later.")
final class RotationFailure extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "payments", Algorithm.AES256_GCM, Duration.ofHours(1)));
    call(new MakeResourceOlder("keys/payments", Duration.ofMinutes(61)));
    call(new RenameTable("pyrite_key_version", "pyrite_key_version_away"));
    try {
      callAndStopOnFailure(new ExpectStatus(GrpcStatus.INTERNAL, new Encrypt(ANNE, "keys/payments", "card", "")),
          Requirement.KEY_ROTATION_RETRY);
    } finally {
      call(new RenameTable("pyrite_key_version_away", "pyrite_key_version"));
    }
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    callAndStopOnFailure(new ExpectEncryptionVersion("keyVersions/payments-2"), Requirement.KEY_ROTATION_RETRY);
  }
}
