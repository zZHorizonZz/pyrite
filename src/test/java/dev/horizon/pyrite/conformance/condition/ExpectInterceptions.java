package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.FlowContext;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks how many times the interceptor ran, each for a flow of a definition.
 */
public record ExpectInterceptions(int count, String definition) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<FlowContext> seen = environment.get(Value.INTERCEPTIONS);
    assertThat(seen).hasSize(count);
    assertThat(seen).allSatisfy(context -> {
      assertThat(context.definition()).as("definition").isEqualTo(definition);
      assertThat(context.flow()).as("flow").startsWith("flows/");
    });
  }
}
