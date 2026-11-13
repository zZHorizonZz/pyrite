package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.conformance.CodeDelivery;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last start or answer of an exchange sent one code, of six digits, to an address, which a challenge of
 * the exchange names. In the variant with an interceptor, the response carries no code.
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
    Exchange exchange = environment.get(Value.EXCHANGE);
    if (environment.variant().delivery() == CodeDelivery.INTERCEPTOR) {
      assertThat(exchange.getDeliveriesList()).as("the codes in the response").isEmpty();
    }
  }
}
