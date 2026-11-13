package dev.horizon.pyrite.conformance.rotation;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKeyWithExactPeriod;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A rotation period is at most 50 years.")
final class LongPeriod extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateKeyWithExactPeriod(
        "organizations/acme", "eternal", Duration.ofDays(18_263))), Requirement.KEY_PERIOD);
    callAndContinueOnFailure(new CreateKeyWithExactPeriod("organizations/acme", "patient", Duration.ofDays(18_262)),
        Requirement.KEY_PERIOD);
  }
}
