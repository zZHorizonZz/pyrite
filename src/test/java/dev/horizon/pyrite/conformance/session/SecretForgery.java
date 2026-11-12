package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectSecretForgeryRefusal;

@Summary("A token with any character of its secret changed is refused.")
final class SecretForgery extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new ExpectSecretForgeryRefusal(), Requirement.SESSION_FORGERY);
  }
}
