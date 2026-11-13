package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the result of the last exchange names one factor, of a kind and an account.
 */
public record ExpectFactorResult(Factor.Kind kind, String account) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.EXCHANGE).getResult().getFactorsList()).singleElement().satisfies(factor -> {
      assertThat(factor.getKind()).as("kind").isEqualTo(kind);
      assertThat(factor.getAccount()).as("account").isEqualTo(account);
    });
  }
}
