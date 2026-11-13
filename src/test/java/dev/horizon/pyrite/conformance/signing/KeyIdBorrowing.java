package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.ForgeToken;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.TokenForgery;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.Rejected;

@Summary("A token of another key with its key id changed to this key's fails its signature.")
final class KeyIdBorrowing extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new CreateKey("organizations/acme", "other", Algorithm.ES256));
    call(new SignToken(ANNE, "keys/other", 600, "at+jwt"));
    call(new ForgeToken(TokenForgery.OAUTH_KEY_ID));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndStopOnFailure(new ExpectRejection(Rejected.Reason.SIGNATURE), Requirement.JWT_FORGERY);
  }
}
