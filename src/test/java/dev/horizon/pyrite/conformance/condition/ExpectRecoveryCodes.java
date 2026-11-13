package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.HashSet;
import java.util.List;

/**
 * Checks that the last enrolment issued a number of recovery codes, each different.
 */
public record ExpectRecoveryCodes(int count) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<String> codes = environment.get(Value.RECOVERY_CODES);
    assertThat(codes).hasSize(count);
    assertThat(new HashSet<>(codes)).as("the different codes").hasSize(count);
  }
}
