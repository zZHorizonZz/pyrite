package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectEncryptionVersion;
import dev.horizon.pyrite.conformance.condition.MakeResourceOlder;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import java.time.Duration;

@Summary("A key rotates at its first use after its period, once.")
final class RotationPeriod extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "payments", Algorithm.AES256_GCM, Duration.ofHours(1)));
    call(new MakeResourceOlder("keys/payments", Duration.ofMinutes(59)));
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    callAndStopOnFailure(new ExpectEncryptionVersion("keyVersions/payments-1"), Requirement.KEY_SCHEDULE);
    call(new MakeResourceOlder("keys/payments", Duration.ofMinutes(2)));
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    callAndStopOnFailure(new ExpectEncryptionVersion("keyVersions/payments-2"), Requirement.KEY_SCHEDULE);
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    callAndStopOnFailure(new ExpectEncryptionVersion("keyVersions/payments-2"), Requirement.KEY_SCHEDULE);
  }
}
