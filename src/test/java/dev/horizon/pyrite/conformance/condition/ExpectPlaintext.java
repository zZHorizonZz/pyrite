package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks the plaintext of the last decryption.
 */
public record ExpectPlaintext(String plaintext) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.PLAINTEXT).toStringUtf8()).isEqualTo(plaintext);
  }
}
