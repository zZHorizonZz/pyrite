package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last exchange is done with a result that holds no session and nothing issued.
 */
public record ExpectEmptyResult() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    Exchange exchange = environment.get(Value.EXCHANGE);
    assertThat(exchange.hasResult()).as("done").isTrue();
    assertThat(exchange.getResult().getSessionsCount()).as("sessions").isZero();
    assertThat(exchange.getResult().getIssuedCount()).as("issued").isZero();
  }
}
