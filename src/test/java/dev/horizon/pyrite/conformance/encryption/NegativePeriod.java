package dev.horizon.pyrite.conformance.encryption;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A rotation period is not negative.")
final class NegativePeriod extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateKey("organizations/acme", "invalid", Algorithm.AES256_GCM,
            Duration.ofSeconds(-1))),
        Requirement.KEY_CREATE);
  }
}
