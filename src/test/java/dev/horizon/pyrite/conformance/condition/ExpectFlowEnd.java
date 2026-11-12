package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the interceptor saw one end of a flow of a definition and an account, with the session that the
 * declared steps issued in its result.
 */
public record ExpectFlowEnd(String definition, String account) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.INTERCEPTIONS)).singleElement().satisfies(context -> {
      assertThat(context.definition()).as("definition").isEqualTo(definition);
      assertThat(context.account()).as("account").contains(account);
      assertThat(context.result().getSessionsCount()).as("sessions").isEqualTo(1);
    });
  }
}
