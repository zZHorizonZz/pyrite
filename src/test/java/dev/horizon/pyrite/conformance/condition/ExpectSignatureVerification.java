package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;

/**
 * Checks that the last verification verified, with a version when the version is not empty.
 */
public record ExpectSignatureVerification(String version) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    VerifyResponse verdict = environment.get(Value.VERIFICATION);
    assertThat(verdict.hasVerified()).as("verified: " + verdict).isTrue();
    if (!version.isEmpty()) {
      assertThat(verdict.getVerified().getKeyVersion()).isEqualTo(version);
    }
  }
}
