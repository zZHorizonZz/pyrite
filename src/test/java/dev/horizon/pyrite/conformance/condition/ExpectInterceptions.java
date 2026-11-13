package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.ExchangeContext;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks how many times the interceptor ran, each for an exchange of a definition.
 */
public record ExpectInterceptions(int count, String definition) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<ExchangeContext> contexts = environment.get(Value.INTERCEPTIONS);
    assertThat(contexts).hasSize(count);
    assertThat(contexts).allSatisfy(context -> {
      assertThat(context.definition()).as("definition").isEqualTo(definition);
      assertThat(context.exchange()).as("exchange").startsWith("exchanges/");
    });
  }
}
