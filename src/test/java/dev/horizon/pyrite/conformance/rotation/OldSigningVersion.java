package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectSignatureVerification;
import dev.horizon.pyrite.conformance.condition.RotateKey;
import dev.horizon.pyrite.conformance.condition.SignData;
import dev.horizon.pyrite.conformance.condition.VerifyData;
import dev.horizon.pyrite.crypto.v1.Algorithm;

@Summary("A signing key's old version still verifies what it signed.")
final class OldSigningVersion extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "codes", Algorithm.HMAC_SHA256));
    call(new SignData(ANNE, "keys/codes", "482913", Value.SIGNATURE));
    call(new RotateKey(ANNE, "keys/codes"));
    callAndStopOnFailure(new VerifyData(ANNE, "keys/codes", "482913", Value.SIGNATURE, ""), Requirement.KEY_ROTATION);
    callAndStopOnFailure(new ExpectSignatureVerification("keyVersions/codes-1"), Requirement.KEY_ROTATION);
  }
}
