package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Replaces the last token with a forgery of it.
 */
public record ForgeToken(TokenForgery forgery) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.put(Value.TOKEN, forgery.apply(environment.get(Value.TOKEN)));
  }
}
