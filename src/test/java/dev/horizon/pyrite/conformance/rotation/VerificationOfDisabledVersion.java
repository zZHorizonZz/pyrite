package dev.horizon.pyrite.conformance.rotation;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.RotateKey;
import dev.horizon.pyrite.conformance.condition.SetVersionState;
import dev.horizon.pyrite.conformance.condition.SignData;
import dev.horizon.pyrite.conformance.condition.VerifyData;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import dev.horizon.pyrite.crypto.v1.Rejected;
import java.util.Set;

@Summary("A disabled version is rejected when it verifies.")
final class VerificationOfDisabledVersion extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "codes", Algorithm.HMAC_SHA256));
    call(new SignData(ANNE, "keys/codes", "482913", Value.SIGNATURE));
    call(new RotateKey(ANNE, "keys/codes"));
    call(new SetVersionState("keyVersions/codes-1", 1, KeyVersion.State.DISABLED));
    call(new VerifyData(ANNE, "keys/codes", "482913", Value.SIGNATURE, "keyVersions/codes-1"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.DISABLED_VERSION), Requirement.KEY_VERSION_STATE);
    call(new VerifyData(ANNE, "keys/codes", "482913", Value.SIGNATURE, ""));
    callAndContinueOnFailure(new ExpectRejection(Set.of(Rejected.Reason.values())), Requirement.KEY_VERSION_STATE);
  }
}
