package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.ForgeToken;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.TokenForgery;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Rejected;

@Summary("A token with a changed payload fails its signature.")
final class PayloadForgery extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
    call(new ForgeToken(TokenForgery.PAYLOAD));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndStopOnFailure(new ExpectRejection(Rejected.Reason.SIGNATURE), Requirement.JWT_FORGERY);
  }
}
