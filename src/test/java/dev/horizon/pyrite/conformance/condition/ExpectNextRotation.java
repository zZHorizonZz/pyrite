package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Key;
import java.time.Duration;

/**
 * Checks that the next rotation of the last key is a period after its last change, on the clock of the database.
 */
public record ExpectNextRotation(Duration period) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    Key key = environment.get(Value.KEY);
    assertThat(key.getNextRotationTime().getSeconds() - key.getUpdateTime().getSeconds()).isCloseTo(
        period.toSeconds(), within(1L));
  }
}
