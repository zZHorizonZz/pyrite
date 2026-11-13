package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectSignatureVerification;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Signing and verifying need their relations.")
final class SigningRelation extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new VerifyToken(BOB, "keys/oauth")),
        Requirement.KEY_PERMISSION);
    call(new GrantRelation(ANNE, "keys/oauth", "verifier", "user:*"));
    callAndContinueOnFailure(new VerifyToken(BOB, "keys/oauth"), Requirement.KEY_PERMISSION);
    callAndContinueOnFailure(new ExpectSignatureVerification(""), Requirement.KEY_PERMISSION);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new SignToken(BOB, "keys/oauth", 600, "at+jwt")),
        Requirement.KEY_PERMISSION);
  }
}
