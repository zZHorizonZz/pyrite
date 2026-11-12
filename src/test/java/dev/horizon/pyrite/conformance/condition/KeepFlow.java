package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Keeps the last flow aside, so that a later flow does not replace it.
 */
public record KeepFlow(Value<Flow> into) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.put(into, environment.get(Value.FLOW));
  }
}
