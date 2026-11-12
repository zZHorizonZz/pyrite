package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Encrypt;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A missing key does not encrypt.")
final class MissingKey extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new Encrypt(ANNE, "keys/missing", "x", "")),
        Requirement.KEY_NAME);
  }
}
