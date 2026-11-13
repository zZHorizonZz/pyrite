package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.ForgeSignature;
import dev.horizon.pyrite.conformance.condition.SignData;
import dev.horizon.pyrite.conformance.condition.VerifyData;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.Rejected;
import java.util.List;

@Summary("A signature of another key, an empty one, or a changed one is rejected.")
final class DetachedForgery extends KeyModule {

  @Override
  protected void run() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "codes", Algorithm.HMAC_SHA256));
    call(new CreateKey("organizations/acme", "other", Algorithm.HMAC_SHA256));
    call(new SignData(ANNE, "keys/codes", "482913", Value.SIGNATURE));
    call(new SignData(ANNE, "keys/other", "482913", Value.OTHER_SIGNATURE));
    call(new VerifyData(ANNE, "keys/codes", "482913", Value.OTHER_SIGNATURE, ""));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.SIGNATURE), Requirement.KEY_SIGNATURE);
    for (boolean empty : List.of(true, false)) {
      call(new ForgeSignature(empty));
      call(new VerifyData(ANNE, "keys/codes", "482913", Value.SIGNATURE_FORGERY, ""));
      callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.SIGNATURE), Requirement.KEY_SIGNATURE);
    }
  }
}
