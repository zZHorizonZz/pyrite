package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import java.time.Duration;

/**
 * Makes the resource of a name older, as if the time went on: the database is Pyrite's clock, so it moves the times
 * of the resource's row back.
 */
public record MakeResourceOlder(String name, Duration by) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().older(name, by);
  }
}
