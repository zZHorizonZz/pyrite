package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.SetToken;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Rejected;
import java.util.List;

@Summary("Text that is not a token is rejected.")
final class JwtSyntax extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    for (String token : List.of("", "not a token", "a.b.c", "....", "e30.e30.")) {
      call(new SetToken(token));
      call(new VerifyToken(ANNE, "keys/oauth"));
      callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.MALFORMED), Requirement.JWT_FORGERY);
    }
  }
}
