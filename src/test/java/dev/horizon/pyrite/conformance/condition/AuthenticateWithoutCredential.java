package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Asks for the principal of a request that names no credential.
 */
public record AuthenticateWithoutCredential() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().authentication.authenticate(AuthenticateRequest.getDefaultInstance()).await(TIMEOUT);
  }
}
