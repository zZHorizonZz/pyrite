package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Takes a text as the last token, such as one that is not a token at all.
 */
public record SetToken(String token) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.put(Value.TOKEN, token);
  }
}
