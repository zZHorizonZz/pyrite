package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignEmpty;
import dev.horizon.pyrite.conformance.condition.VerifyEmpty;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A request names what to sign and what to verify.")
final class EmptySigningRequest extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new SignEmpty(ANNE, "keys/oauth")),
        Requirement.KEY_REQUEST);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new VerifyEmpty(ANNE, "keys/oauth")),
        Requirement.KEY_REQUEST);
  }
}
