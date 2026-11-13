package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectEncryptionVersion;
import dev.horizon.pyrite.conformance.condition.ExpectKeyRevision;
import dev.horizon.pyrite.conformance.condition.ExpectNextRotation;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GetKey;
import dev.horizon.pyrite.conformance.condition.MakeResourceOlder;
import dev.horizon.pyrite.conformance.condition.UpdateKey;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A key's rotation period changes at its revision, and runs from the change.")
final class PeriodChange extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new MakeResourceOlder("keys/payments", Duration.ofDays(1)));
    call(new GetKey(ANNE, "keys/payments", Value.KEY_BEFORE));
    callAndStopOnFailure(new UpdateKey(ANNE, Value.KEY_BEFORE, Duration.ofHours(1)), Requirement.KEY_SCHEDULE);
    callAndContinueOnFailure(new ExpectKeyRevision(Value.KEY_BEFORE, 1), Requirement.KEY_SCHEDULE);
    callAndContinueOnFailure(new ExpectNextRotation(Duration.ofHours(1)), Requirement.KEY_SCHEDULE);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ABORTED, new UpdateKey(ANNE, Value.KEY_BEFORE, Duration.ZERO)),
        Requirement.KEY_SCHEDULE);
    call(new MakeResourceOlder("keys/payments", Duration.ofMinutes(61)));
    call(new Encrypt(ANNE, "keys/payments", "card", ""));
    callAndContinueOnFailure(new ExpectEncryptionVersion("keyVersions/payments-2"), Requirement.KEY_SCHEDULE);
  }
}
