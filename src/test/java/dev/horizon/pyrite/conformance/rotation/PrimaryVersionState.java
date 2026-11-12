package dev.horizon.pyrite.conformance.rotation;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.DeleteKeyVersion;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SetVersionState;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import io.vertx.grpc.common.GrpcStatus;

@Summary("The primary version is neither disabled nor destroyed.")
final class PrimaryVersionState extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new SetVersionState("keyVersions/payments-1", 1,
        KeyVersion.State.DISABLED)), Requirement.KEY_VERSION_STATE);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new DeleteKeyVersion("keyVersions/payments-1")),
        Requirement.KEY_VERSION_STATE);
  }
}
