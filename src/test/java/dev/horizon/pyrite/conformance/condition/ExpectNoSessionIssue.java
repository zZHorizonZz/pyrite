package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the handlers of sessions heard of no issued session.
 */
public record ExpectNoSessionIssue() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.SESSION_ISSUES)).as("issued").isEmpty();
  }
}
