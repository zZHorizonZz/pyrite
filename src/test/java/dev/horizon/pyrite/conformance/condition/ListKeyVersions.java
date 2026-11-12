package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsRequest;
import java.util.List;

/**
 * Lists the versions of a key, as a subject, and leaves them.
 */
public record ListKeyVersions(String subject, String key) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.KEY_VERSIONS, List.copyOf(environment.pyrite().cryptography.listKeyVersions(
        ListKeyVersionsRequest.newBuilder().setSubject(subject).setKey(key).build()).await(TIMEOUT)
        .getVersionsList()));
  }
}
