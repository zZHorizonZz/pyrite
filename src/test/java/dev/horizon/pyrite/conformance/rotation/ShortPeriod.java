package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateKeyWithExactPeriod;
import dev.horizon.pyrite.conformance.condition.ExpectNextRotation;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GetKey;
import dev.horizon.pyrite.conformance.condition.UpdateKeyWithExactPeriod;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A rotation period other than zero is at least a minute, when a key is created and when it changes.")
final class ShortPeriod extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateKeyWithExactPeriod(
        "organizations/acme", "hasty", Duration.ofSeconds(59))), Requirement.KEY_PERIOD);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateKeyWithExactPeriod(
        "organizations/acme", "instant", Duration.ofMillis(500))), Requirement.KEY_PERIOD);
    callAndStopOnFailure(new CreateKeyWithExactPeriod("organizations/acme", "brisk", Duration.ofMinutes(1)),
        Requirement.KEY_PERIOD);
    callAndContinueOnFailure(new ExpectNextRotation(Duration.ofMinutes(1)), Requirement.KEY_PERIOD);
    call(new GetKey(ANNE, "keys/payments", Value.KEY_BEFORE));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new UpdateKeyWithExactPeriod(
        Value.KEY_BEFORE, Duration.ofSeconds(30))), Requirement.KEY_PERIOD);
  }
}
