package dev.horizon.pyrite.conformance;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.condition.CreateKey;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.crypto.v1.Algorithm;

/**
 * A scenario of keys, which starts with anne's organization acme.
 */
public abstract class KeyModule extends ConformanceModule {

  /**
   * Makes acme known, as anne.
   */
  protected final void prepareOrganization() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
  }

  /**
   * Makes acme known, with the encryption key payments under it.
   */
  protected final void preparePayments() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "payments", Algorithm.AES256_GCM));
  }

  /**
   * Makes acme known, with the signing key oauth under it.
   */
  protected final void prepareOauth() {
    prepareOrganization();
    call(new CreateKey("organizations/acme", "oauth", Algorithm.ES256));
  }
}
