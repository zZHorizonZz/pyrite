package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.CreateKeyRequest;
import java.time.Duration;

/**
 * Creates an encryption key under a parent, as anne, with a rotation period sent to the nanosecond, and leaves it.
 */
public record CreateKeyWithExactPeriod(String parent, String id, Duration period) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.KEY, environment.pyrite().cryptography.createKey(CreateKeyRequest.newBuilder()
        .setSubject(ANNE).setKeyId(id).setParent(parent).setAlgorithm(Algorithm.AES256_GCM).setRotationPeriod(
            com.google.protobuf.Duration.newBuilder().setSeconds(period.getSeconds()).setNanos(period.getNano()))
        .build()).await(TIMEOUT));
  }
}
