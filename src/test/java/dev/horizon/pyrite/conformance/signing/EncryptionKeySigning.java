package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignToken;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An encryption key does not sign.")
final class EncryptionKeySigning extends KeyModule {

  @Override
  protected void run() {
    preparePayments();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new SignToken(ANNE, "keys/payments", 600, "at+jwt")),
        Requirement.KEY_PURPOSE);
  }
}
