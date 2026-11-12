package dev.horizon.pyrite.conformance.passkey;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.condition.AnswerPasskeyCreation;
import dev.horizon.pyrite.conformance.condition.CreatePasskeyClient;
import dev.horizon.pyrite.conformance.condition.StartFlow;

/**
 * A scenario of passkeys, with a browser at the origin of Acme that keeps the passkeys that it makes.
 */
abstract class PasskeyModule extends AccountModule {

  static final String ORIGIN = "https://acme.dev";

  /**
   * Makes the browser, and enrols a passkey for anne with it.
   */
  final void enrolPasskey() {
    call(new CreatePasskeyClient(ORIGIN));
    call(new StartFlow(ANNE, "enrol-passkey", ""));
    call(new AnswerPasskeyCreation());
  }
}
