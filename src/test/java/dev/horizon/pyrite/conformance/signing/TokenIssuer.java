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

@Summary("A token of another issuer is rejected.")
final class TokenIssuer extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/oauth", "https://other.example", SignToken.AUDIENCE, 30));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.ISSUER), Requirement.JWT_CLAIMS);
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt", Map.of("iss", SignToken.ABSENT)));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.ISSUER), Requirement.JWT_CLAIMS);
  }
}
