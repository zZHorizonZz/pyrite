package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.RotateKey;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A rotation needs the admin relation on the key.")
final class RotationPermission extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    call(new GrantRelation(ANNE, "keys/payments", "encrypter", BOB));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new RotateKey(BOB, "keys/payments")),
        Requirement.KEY_PERMISSION);
  }
}
