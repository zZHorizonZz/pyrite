package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectClaim;
import dev.horizon.pyrite.conformance.condition.ExpectPublicKeyVerification;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import java.util.List;

@Summary("A signing key's JWT verifies with its published public key, and with Pyrite.")
final class PublicKeyPublication extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    for (Algorithm algorithm : List.of(Algorithm.RS256, Algorithm.ES256, Algorithm.EDDSA)) {
      String key = "keys/" + algorithm.name().toLowerCase();
      call(new CreateKey("organizations/acme", algorithm.name().toLowerCase(), algorithm));
      call(new SignToken(ANNE, key, 600, "at+jwt"));
      callAndContinueOnFailure(new VerifyToken(ANNE, key), Requirement.KEY_JWT);
      callAndContinueOnFailure(new ExpectClaim("sub", ANNE), Requirement.KEY_JWT);
      callAndContinueOnFailure(new ExpectPublicKeyVerification(key, algorithm), Requirement.KEY_JWT);
    }
  }
}
