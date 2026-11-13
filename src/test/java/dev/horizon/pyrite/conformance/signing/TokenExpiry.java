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

@Summary("A token that expired, or that has no expiry, is rejected.")
final class TokenExpiry extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", -60, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.EXPIRED), Requirement.JWT_TIME);
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt", Map.of("exp", SignToken.ABSENT)));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.EXPIRED), Requirement.JWT_TIME);
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt", Map.of("exp", "tomorrow")));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.EXPIRED), Requirement.JWT_TIME);
  }
}
