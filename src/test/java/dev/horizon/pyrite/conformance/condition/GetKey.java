package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.GetKeyRequest;
import dev.horizon.pyrite.crypto.v1.Key;

/**
 * Reads a key, as a subject, and keeps it.
 */
public record GetKey(String subject, String key, Value<Key> into) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(into, environment.pyrite().cryptography.getKey(GetKeyRequest.newBuilder().setSubject(subject)
        .setName(key).build()).await(TIMEOUT));
  }
}
