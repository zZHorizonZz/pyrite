package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Asks for the principal of an access token as it is written.
 */
public record AuthenticateToken(String token) implements Condition {

  /**
   * Asks for the principal of an access token.
   */
  public static Principal principal(Environment environment, String token) throws Exception {
    return environment.pyrite().authentication.authenticate(AuthenticateRequest.newBuilder().setAccessToken(token)
        .build()).await(TIMEOUT);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    principal(environment, token);
  }
}
