package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectSecretBorrowingRefusal;

@Summary("A token cut short, or with the secret of another session, is refused.")
final class SecretBorrowing extends SessionModule {

  @Override
  protected void run() {
    signIn();
    callAndStopOnFailure(new ExpectSecretBorrowingRefusal(EMAIL, PASSWORD), Requirement.SESSION_FORGERY);
  }
}
