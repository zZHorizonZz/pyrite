package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Rejected;
import java.util.Map;

@Summary("A token that is not yet valid is rejected.")
final class TokenNotBefore extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt", Map.of("nbf", 300)));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndStopOnFailure(new ExpectRejection(Rejected.Reason.NOT_YET_VALID), Requirement.JWT_TIME);
  }
}
