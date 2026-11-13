package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last exchange is not done.
 */
public record ExpectNoResult() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.EXCHANGE).hasResult()).isFalse();
  }
}
