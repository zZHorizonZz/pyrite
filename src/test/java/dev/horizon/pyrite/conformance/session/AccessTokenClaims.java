package dev.horizon.pyrite.conformance.session;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectAccessTokenClaims;

@Summary("An access token is a JWT that a service verifies with the access token key, with its claims.")
final class AccessTokenClaims extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new ExpectAccessTokenClaims(ANNE), Requirement.ACCESS_TOKEN_CLAIMS);
  }
}
