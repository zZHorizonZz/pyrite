package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks the subjects of the last list, sorted.
 */
public record ExpectSubjects(List<String> subjects) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.SUBJECTS)).containsExactlyElementsOf(subjects.stream().sorted().toList());
  }
}
