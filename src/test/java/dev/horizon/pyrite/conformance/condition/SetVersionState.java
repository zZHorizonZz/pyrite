package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import dev.horizon.pyrite.crypto.v1.UpdateKeyVersionRequest;

/**
 * Sets the state of a key version at its revision, as anne.
 */
public record SetVersionState(String version, long revision, KeyVersion.State state) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.pyrite().cryptography.updateKeyVersion(UpdateKeyVersionRequest.newBuilder().setSubject(ANNE)
        .setName(version).setState(state).setRevision(revision).build()).await(TIMEOUT);
  }
}
