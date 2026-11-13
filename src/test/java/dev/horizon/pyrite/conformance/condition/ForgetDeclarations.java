package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Deletes every declaration, as if Pyrite had none yet.
 */
public record ForgetDeclarations() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().forgetDeclarations();
  }
}
