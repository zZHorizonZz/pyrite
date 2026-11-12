package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks the primary version of the last key.
 */
public record ExpectPrimaryVersion(String version) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.KEY).getPrimaryVersion()).isEqualTo(version);
  }
}
