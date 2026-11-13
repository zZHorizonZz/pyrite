package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.SignData;
import dev.horizon.pyrite.conformance.condition.VerifyData;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.Rejected;
import java.util.List;

@Summary("A named version that the key does not have is rejected.")
final class UnknownSigningVersion extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "codes", Algorithm.HMAC_SHA256));
    call(new CreateKey("organizations/acme", "other", Algorithm.HMAC_SHA256));
    call(new SignData(ANNE, "keys/codes", "482913", Value.SIGNATURE));
    for (String version : List.of("keyVersions/other-1", "keyVersions/codes-7")) {
      call(new VerifyData(ANNE, "keys/codes", "482913", Value.SIGNATURE, version));
      callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.UNKNOWN_VERSION), Requirement.KEY_SIGNATURE);
    }
  }
}
