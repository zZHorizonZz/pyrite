package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Authenticates the access token of the last session, and leaves the principal.
 */
public record Authenticate() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.PRINCIPAL, AuthenticateToken.principal(environment, environment.get(Value.ACCESS_TOKEN)));
  }
}
