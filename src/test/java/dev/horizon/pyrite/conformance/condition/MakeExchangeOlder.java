package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.time.Duration;

/**
 * Makes the last exchange older, as if the time went on: the database is Pyrite's clock, so it moves the times of the
 * exchange's row back.
 */
public record MakeExchangeOlder(Duration by) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().older(environment.get(Value.EXCHANGE).getName(), by);
  }
}
