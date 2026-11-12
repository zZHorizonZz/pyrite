package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Signs in again, and checks that the sign-in gives a token of its own.
 */
public record ExpectNewToken(String email, String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    assertThat(SignIn.token(environment, email, password)).isNotEqualTo(environment.get(Value.SESSION_TOKEN));
  }
}
