package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.time.Duration;

/**
 * Makes the last declaration read older, as if the time went on: the database is Pyrite's clock, so it moves the time
 * of its row back.
 */
public record MakeDeclarationOlder(Duration by) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().olderDeclaration(environment.get(Value.DECLARATION).getId(), by);
  }
}
