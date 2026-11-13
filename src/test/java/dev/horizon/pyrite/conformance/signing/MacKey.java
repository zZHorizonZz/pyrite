package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectNoPublicKey;
import dev.horizon.pyrite.conformance.condition.ExpectSignatureVerification;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Algorithm;

@Summary("A MAC key signs a JWT, and publishes no key.")
final class MacKey extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "codes", Algorithm.HMAC_SHA256));
    call(new SignToken(ANNE, "keys/codes", 600, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/codes"));
    callAndContinueOnFailure(new ExpectSignatureVerification(""), Requirement.KEY_JWT);
    callAndContinueOnFailure(new ExpectNoPublicKey("keys/codes", "keyVersions/codes-1"), Requirement.KEY_JWT);
  }
}
