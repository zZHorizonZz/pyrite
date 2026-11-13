package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks the key version that the last encryption names.
 */
public record ExpectEncryptionVersion(String version) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.ENCRYPTION).getKeyVersion()).isEqualTo(version);
  }
}
