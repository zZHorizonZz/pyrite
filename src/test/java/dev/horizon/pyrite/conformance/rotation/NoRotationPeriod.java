package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectEncryptionVersion;
import dev.horizon.pyrite.conformance.condition.MakeResourceOlder;
import java.time.Duration;

@Summary("A key without a period never rotates on its own.")
final class NoRotationPeriod extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new MakeResourceOlder("keys/payments", Duration.ofDays(3650)));
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    callAndStopOnFailure(new ExpectEncryptionVersion("keyVersions/payments-1"), Requirement.KEY_SCHEDULE);
  }
}
