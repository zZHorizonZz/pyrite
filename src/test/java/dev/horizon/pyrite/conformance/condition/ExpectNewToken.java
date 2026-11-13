package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Signs in again, and checks that the sign-in gives a session and tokens of its own.
 */
public record ExpectNewToken(String email, String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    TokenSet tokens = SignIn.tokens(environment, email, password);
    assertThat(tokens.getSession()).as("session").isNotEqualTo(environment.get(Value.SESSION));
    assertThat(tokens.getAccessToken()).as("access token").isNotEqualTo(environment.get(Value.ACCESS_TOKEN));
    assertThat(tokens.getRefreshToken()).as("refresh token").isNotEqualTo(environment.get(Value.REFRESH_TOKEN));
  }
}
