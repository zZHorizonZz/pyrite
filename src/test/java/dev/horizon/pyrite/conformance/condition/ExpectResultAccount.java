package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks the account that the result of the last flow names.
 */
public record ExpectResultAccount(String account) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.FLOW).getResult().getAccount()).isEqualTo(account);
  }
}
