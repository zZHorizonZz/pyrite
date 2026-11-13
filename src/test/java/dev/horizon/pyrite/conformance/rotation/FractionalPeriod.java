package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateKeyWithExactPeriod;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GetKey;
import dev.horizon.pyrite.conformance.condition.UpdateKeyWithExactPeriod;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A rotation period is a whole number of seconds, when a key is created and when it changes.")
final class FractionalPeriod extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateKeyWithExactPeriod(
        "organizations/acme", "fraction", Duration.ofSeconds(90, 500_000_000))), Requirement.KEY_PERIOD);
    call(new GetKey(ANNE, "keys/payments", Value.KEY_BEFORE));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new UpdateKeyWithExactPeriod(
        Value.KEY_BEFORE, Duration.ofSeconds(3600, 1))), Requirement.KEY_PERIOD);
  }
}
