package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the ciphertext of the last encryption does not show a part of its plaintext.
 */
public record ExpectPlaintextSecrecy(String part) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.CIPHERTEXT).toStringUtf8()).doesNotContain(part);
  }
}
