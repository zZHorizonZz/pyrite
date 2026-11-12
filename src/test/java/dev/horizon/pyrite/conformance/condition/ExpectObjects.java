package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks the objects of the last list, in order.
 */
public record ExpectObjects(List<String> objects) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.OBJECTS)).containsExactlyElementsOf(objects);
  }
}
