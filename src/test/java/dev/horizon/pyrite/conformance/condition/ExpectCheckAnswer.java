package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks the answer of the last check.
 */
public record ExpectCheckAnswer(boolean allowed) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.ALLOWED)).isEqualTo(allowed);
  }
}
