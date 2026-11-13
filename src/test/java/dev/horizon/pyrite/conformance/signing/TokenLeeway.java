package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.ExpectSignatureVerification;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Rejected;

@Summary("The leeway covers clock skew, and no more; without one, the clock skew of the options does.")
final class TokenLeeway extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", -20, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectSignatureVerification(""), Requirement.JWT_TIME);
    call(new VerifyToken(ANNE, "keys/oauth", SignToken.ISSUER, SignToken.AUDIENCE, -1));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.EXPIRED), Requirement.JWT_TIME);
    call(new SignToken(ANNE, "keys/oauth", -2, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/oauth", SignToken.ISSUER, SignToken.AUDIENCE, -1));
    callAndContinueOnFailure(new ExpectSignatureVerification(""), Requirement.JWT_TIME);
    call(new SignToken(ANNE, "keys/oauth", -31, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.EXPIRED), Requirement.JWT_TIME);
  }
}
