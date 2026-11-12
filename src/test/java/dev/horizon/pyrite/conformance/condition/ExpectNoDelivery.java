package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last start or answer of a flow sent no code.
 */
public record ExpectNoDelivery() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.sent()).as("the codes sent").isEmpty();
    assertThat(environment.get(Value.FLOW).getDeliveriesList()).as("the codes in the response").isEmpty();
  }
}
