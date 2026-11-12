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
import java.util.Set;

@Summary("A token that claims no algorithm is rejected.")
final class AlgorithmNone extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    call(new SignToken(ANNE, "keys/oauth", 600, "at+jwt"));
    call(new ForgeToken(TokenForgery.ALGORITHM_NONE));
    call(new VerifyToken(ANNE, "keys/oauth"));
    callAndStopOnFailure(new ExpectRejection(Set.of(Rejected.Reason.MALFORMED, Rejected.Reason.ALGORITHM)),
        Requirement.JWT_FORGERY);
  }
}
