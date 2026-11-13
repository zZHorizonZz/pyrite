package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the handlers of sessions heard of the last session once as issued and once as ended.
 */
public record ExpectSessionObservation() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    String session = environment.get(Value.SESSION);
    assertThat(environment.get(Value.SESSION_ISSUES)).as("issued").containsExactly(session);
    assertThat(environment.get(Value.SESSION_ENDS)).as("ended").containsExactly(session);
  }
}
