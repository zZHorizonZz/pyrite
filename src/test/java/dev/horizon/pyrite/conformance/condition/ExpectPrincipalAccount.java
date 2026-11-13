package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last principal is of an account.
 */
public record ExpectPrincipalAccount(String account) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.PRINCIPAL).getAccount()).as("account").isEqualTo(account);
  }
}
