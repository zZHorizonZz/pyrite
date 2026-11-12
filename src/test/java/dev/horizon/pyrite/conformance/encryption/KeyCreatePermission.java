package dev.horizon.pyrite.conformance.encryption;

import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("Creating a key needs the create permission on its parent.")
final class KeyCreatePermission extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new CreateKey(BOB, "organizations/acme", "mine", Algorithm.AES256_GCM,
            Duration.ZERO)),
        Requirement.KEY_CREATE);
  }
}
