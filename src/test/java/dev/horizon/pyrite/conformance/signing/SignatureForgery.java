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
import java.util.List;
import java.util.Set;

@Summary("A token with a changed or missing signature is rejected.")
final class SignatureForgery extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    for (TokenForgery forgery : List.of(TokenForgery.REVERSED_SIGNATURE, TokenForgery.EMPTY_SIGNATURE)) {
      call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
      call(new ForgeToken(forgery));
      call(new VerifyToken(ANNE, "keys/oauth"));
      callAndContinueOnFailure(new ExpectRejection(Set.of(Rejected.Reason.SIGNATURE, Rejected.Reason.MALFORMED)),
          Requirement.JWT_FORGERY);
    }
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
    call(new ForgeToken(TokenForgery.NO_SIGNATURE));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.MALFORMED), Requirement.JWT_FORGERY);
  }
}
