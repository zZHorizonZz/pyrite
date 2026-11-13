package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks how many rows the last purge deleted.
 */
public record ExpectPurgeCount(long count) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.PURGE_COUNT)).isEqualTo(count);
  }
}
