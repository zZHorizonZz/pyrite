package dev.horizon.pyrite.conformance.encryption;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A key names its algorithm, which decides its purpose.")
final class KeyAlgorithm extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateKey("organizations/acme", "invalid",
        Algorithm.ALGORITHM_UNSPECIFIED)), Requirement.KEY_CREATE);
  }
}
