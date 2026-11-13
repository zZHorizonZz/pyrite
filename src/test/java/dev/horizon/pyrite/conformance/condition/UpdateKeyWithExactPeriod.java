package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Key;
import dev.horizon.pyrite.crypto.v1.UpdateKeyRequest;
import java.time.Duration;

/**
 * Changes the rotation period of a key kept aside, at its revision then, as anne, with the period sent to the
 * nanosecond, and leaves the key.
 */
public record UpdateKeyWithExactPeriod(Value<Key> at, Duration period) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    Key key = environment.get(at);
    environment.put(Value.KEY, environment.pyrite().cryptography.updateKey(UpdateKeyRequest.newBuilder()
        .setSubject(ANNE).setName(key.getName()).setRevision(key.getRevision()).setRotationPeriod(
            com.google.protobuf.Duration.newBuilder().setSeconds(period.getSeconds()).setNanos(period.getNano()))
        .build()).await(TIMEOUT));
  }
}
