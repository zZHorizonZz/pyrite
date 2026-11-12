package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.RotateKey;
import dev.horizon.pyrite.conformance.condition.SetVersionState;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A version is ENABLED or DISABLED.")
final class VersionStateValue extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new RotateKey(ANNE, "keys/payments"));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new SetVersionState("keyVersions/payments-1", 1,
        KeyVersion.State.STATE_UNSPECIFIED)), Requirement.KEY_VERSION_STATE);
  }
}
