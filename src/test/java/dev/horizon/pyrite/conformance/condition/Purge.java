package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Purges what ended, and leaves how many rows the purge deleted.
 */
public record Purge() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.PURGE_COUNT, environment.pyrite().service.purge().await(TIMEOUT));
  }
}
