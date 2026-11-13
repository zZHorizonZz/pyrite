package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the interceptor saw one end of an exchange of a definition and an account.
 */
public record ExpectExchangeEnd(String definition, String account) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.INTERCEPTIONS)).singleElement().satisfies(context -> {
      assertThat(context.definition()).as("definition").isEqualTo(definition);
      assertThat(context.account()).as("account").contains(account);
    });
  }
}
