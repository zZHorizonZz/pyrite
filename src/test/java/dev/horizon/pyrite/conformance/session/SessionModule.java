package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.condition.SignIn;

/**
 * A scenario of sessions, which starts with anne signed in with her password.
 */
abstract class SessionModule extends AccountModule {

  /**
   * Prepares anne's account and signs her in.
   */
  final void signIn() {
    prepareAnne();
    call(new SignIn(EMAIL, PASSWORD));
  }
}
