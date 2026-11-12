package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.CodeSent;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.conformance.CodeDelivery;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last start or answer of a flow sent one code, of six digits, to an address, which a challenge of
 * the flow names. In the variant with an interceptor, the response carries no code.
 */
public record ExpectDelivery(String to, String kind) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    assertThat(environment.sent()).as("the codes sent").hasSize(1);
    Delivery delivery = environment.sent().getFirst();
    assertThat(delivery.getTo()).as("to").isEqualTo(to);
    assertThat(delivery.getKind()).as("kind").isEqualTo(kind);
    assertThat(delivery.getIdentifier()).as("identifier").startsWith("identifiers/");
    assertThat(delivery.getCode()).as("code").matches("[0-9]{6}");
    Flow flow = environment.get(Value.FLOW);
    assertThat(flow.getChallenges(0).getParameters().unpack(CodeSent.class).getIdentifier()).as("the challenge")
        .isEqualTo(delivery.getIdentifier());
    if (environment.variant().delivery() == CodeDelivery.INTERCEPTOR) {
      assertThat(flow.getDeliveriesList()).as("the codes in the response").isEmpty();
    }
  }
}
