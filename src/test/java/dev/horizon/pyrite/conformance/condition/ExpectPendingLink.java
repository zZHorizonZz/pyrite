package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last exchange is not done, and names the provider account of an email that it links once the next
 * answer meets the stage.
 */
public record ExpectPendingLink(String email) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    Exchange exchange = environment.get(Value.EXCHANGE);
    assertThat(exchange.hasResult()).as("done").isFalse();
    assertThat(exchange.getProviderAccount().getEmail()).as("email").isEqualTo(email);
  }
}
