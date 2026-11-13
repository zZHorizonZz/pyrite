package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Identifier;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks the values of the identifiers of the last list.
 */
public record ExpectIdentifierValues(List<String> values) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.IDENTIFIERS)).extracting(Identifier::getValue).containsExactlyElementsOf(
        values);
  }
}
