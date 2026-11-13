package dev.horizon.pyrite.conformance.provider;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.condition.AnswerCode;
import dev.horizon.pyrite.conformance.condition.StartExchange;

/**
 * A scenario of providers, which can start with anne's email address verified.
 */
abstract class ProviderModule extends AccountModule {

  /**
   * Prepares anne's account, and verifies her email address.
   */
  final void prepareVerifiedAnne() {
    prepareAnne();
    call(new StartExchange(ANNE, "verify", EMAIL));
    call(new AnswerCode());
  }
}
