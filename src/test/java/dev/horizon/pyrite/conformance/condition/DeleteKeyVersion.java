package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.crypto.v1.DeleteKeyVersionRequest;

/**
 * Destroys a key version, as anne.
 */
public record DeleteKeyVersion(String version) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().cryptography.deleteKeyVersion(DeleteKeyVersionRequest.newBuilder().setSubject(ANNE)
        .setName(version).build()).await(TIMEOUT);
  }
}
