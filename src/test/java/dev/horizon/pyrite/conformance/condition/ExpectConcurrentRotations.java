package dev.horizon.pyrite.conformance.condition;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.crypto.v1.Key;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsRequest;
import dev.horizon.pyrite.crypto.v1.RotateKeyRequest;
import io.vertx.core.Future;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Rotates a key a number of times at once, as anne, and checks that every rotation succeeds, and that the versions
 * are numbered from 1 without a gap.
 */
public record ExpectConcurrentRotations(String key, int count) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    List<Future<Key>> rotations = LongStream.range(0, count).mapToObj(ignored -> environment.pyrite().cryptography
        .rotateKey(RotateKeyRequest.newBuilder().setSubject(ANNE).setName(key).build())).toList();
    Future.all(rotations).await(TIMEOUT);
    assertThat(environment.pyrite().cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder()
        .setSubject(ANNE).setKey(key).build()).await(TIMEOUT).getVersionsList()).extracting(KeyVersion::getNumber)
        .containsExactlyElementsOf(LongStream.rangeClosed(1, count + 1L).boxed().toList());
  }
}
