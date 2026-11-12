package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the secret of the last TOTP enrolment is not the one kept aside.
 */
public record ExpectDifferentSecrets() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.TOTP_SECRET)).isNotEqualTo(environment.get(Value.OTHER_TOTP_SECRET));
  }
}
