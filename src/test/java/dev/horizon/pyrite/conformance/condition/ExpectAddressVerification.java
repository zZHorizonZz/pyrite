package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last flow marked its identifier verified.
 */
public record ExpectAddressVerification() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.FLOW).getResult().getIdentifiers(0).getVerified()).isTrue();
  }
}
