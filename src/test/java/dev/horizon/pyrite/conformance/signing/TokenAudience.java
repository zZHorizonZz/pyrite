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
import java.util.List;
import java.util.Map;

@Summary("A token for another audience is rejected, and an audience list must hold the expected one.")
final class TokenAudience extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/oauth", SignToken.ISSUER, "https://other.example", 30));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.AUDIENCE), Requirement.JWT_CLAIMS);
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt", Map.of("aud", List.of(SignToken.AUDIENCE,
        "https://admin.acme.dev"))));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectSignatureVerification(""), Requirement.JWT_CLAIMS);
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt", Map.of("aud", List.of("https://admin.acme.dev"))));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.AUDIENCE), Requirement.JWT_CLAIMS);
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt", Map.of("aud", SignToken.ABSENT)));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.AUDIENCE), Requirement.JWT_CLAIMS);
  }
}
