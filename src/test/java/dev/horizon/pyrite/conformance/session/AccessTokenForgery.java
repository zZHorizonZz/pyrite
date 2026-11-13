package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectAccessTokenForgeryRefusal;

@Summary("An access token with changed claims or a changed signature does not authenticate.")
final class AccessTokenForgery extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new ExpectAccessTokenForgeryRefusal(), Requirement.SESSION_FORGERY);
  }
}
