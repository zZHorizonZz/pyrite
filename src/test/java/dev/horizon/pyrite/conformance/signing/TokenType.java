package dev.horizon.pyrite.conformance.signing;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.KeyModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRejection;
import dev.horizon.pyrite.conformance.condition.SignToken;
import dev.horizon.pyrite.conformance.condition.VerifyToken;
import dev.horizon.pyrite.crypto.v1.Rejected;
import java.util.List;

@Summary("A token of another type is rejected.")
final class TokenType extends KeyModule {

  @Override
  protected void run() {
    prepareOauth();
    for (String type : List.of("", "id+jwt")) {
      call(new SignToken(ANNE, "keys/oauth", 600, type));
      call(new VerifyToken(ANNE, "keys/oauth"));
      callAndContinueOnFailure(new ExpectRejection(Rejected.Reason.TYPE), Requirement.JWT_CLAIMS);
    }
  }
}
