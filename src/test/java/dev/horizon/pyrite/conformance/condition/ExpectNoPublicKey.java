package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsRequest;

/**
 * Checks that a version of a key publishes no public key.
 */
public record ExpectNoPublicKey(String key, String version) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    assertThat(environment.pyrite().cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder()
        .setSubject(ANNE).setKey(key).build()).await(TIMEOUT).getVersionsList()).filteredOn(candidate -> candidate
            .getName().equals(version))
        .singleElement().satisfies(found -> assertThat(found.hasPublicKey())
            .isFalse());
  }
}
