package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Keeps the last exchange aside, so that a later exchange does not replace it.
 */
public record KeepExchange(Value<Exchange> into) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.put(into, environment.get(Value.EXCHANGE));
  }
}
