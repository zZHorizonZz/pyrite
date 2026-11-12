package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks the changes of the last list, in order.
 */
public record ExpectChanges(List<String> changes) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.CHANGES)).containsExactlyElementsOf(changes);
  }
}
