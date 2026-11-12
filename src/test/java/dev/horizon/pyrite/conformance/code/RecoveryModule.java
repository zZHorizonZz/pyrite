package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.condition.VerifyAddress;

/**
 * A scenario of recoveries, which starts with anne's email address verified.
 */
abstract class RecoveryModule extends AccountModule {

  /**
   * Prepares anne's account, and verifies her email address.
   */
  final void prepareVerifiedAnne() {
    prepareAnne();
    call(new VerifyAddress(ANNE, EMAIL));
  }
}
