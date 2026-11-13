package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks the names that a handler kept, in order.
 */
public record ExpectNames(Value<List<String>> kept, List<String> names) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(kept)).containsExactlyElementsOf(names);
  }
}
