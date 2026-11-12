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
import java.util.List;

@Summary("A token of another key, or with no key id, names no version of this key.")
final class TokenOfOtherKey extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new CreateKey("organizations/acme", "other", Algorithm.ES256));
    call(new SignToken(ANNE, "keys/other", 600, "at+jwt"));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.UNKNOWN_VERSION), Requirement.JWT_FORGERY);
    for (TokenForgery forgery : List.of(TokenForgery.NO_KEY_ID, TokenForgery.UNKNOWN_KEY_ID)) {
      call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
      call(new ForgeToken(forgery));
      call(new VerifyToken(ANNE, "keys/oauth"));
      callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.UNKNOWN_VERSION), Requirement.JWT_FORGERY);
    }
  }
}
