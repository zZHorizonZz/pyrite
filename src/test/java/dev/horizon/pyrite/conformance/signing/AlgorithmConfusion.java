package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.ForgeAlgorithmConfusion;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Rejected;

@Summary("A token signed with HS256 over the published public key is rejected.")
final class AlgorithmConfusion extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new ForgeAlgorithmConfusion("keys/oauth", "keyVersions/oauth-1"));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndStopOnFailure(new ExpectRejection(Rejected.Reason.ALGORITHM), Requirement.JWT_FORGERY);
  }
}
