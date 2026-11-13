package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.crypto.v1.Algorithm;

/**
 * Creates the organization and the two keys that accounts need: the MAC key of sessions and codes, and the
 * encryption key of factor secrets.
 */
public record PrepareAccounts() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    new CreateObject(ANNE, "organizations/acme", "").evaluate(environment);
    new CreateKey("organizations/acme", "sessions", Algorithm.HMAC_SHA256).evaluate(environment);
    new CreateKey("organizations/acme", "factors", Algorithm.AES256_GCM).evaluate(environment);
    new CreateKey("organizations/acme", "access", Algorithm.ES256).evaluate(environment);
  }
}
