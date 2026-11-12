package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks the grants of the last list, as relation and grantee, sorted.
 */
public record ExpectGrants(List<String> grants) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.GRANTS)).containsExactlyElementsOf(grants);
  }
}
