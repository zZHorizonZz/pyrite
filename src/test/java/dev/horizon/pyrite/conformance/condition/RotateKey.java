package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.RotateKeyRequest;

/**
 * Rotates a key, as a subject, and leaves it.
 */
public record RotateKey(String subject, String key) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.KEY, environment.pyrite().cryptography.rotateKey(RotateKeyRequest.newBuilder()
        .setSubject(subject).setName(key).build()).await(TIMEOUT));
  }
}
