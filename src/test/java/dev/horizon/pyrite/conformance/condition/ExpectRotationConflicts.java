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
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Rotates a key a number of times at once, as anne, and checks that each rotation either succeeds with a version of
 * its own or fails with {@code ABORTED}, and that the key keeps its first version and the versions of the rotations
 * that succeeded, and no other.
 */
public record ExpectRotationConflicts(String key, int count) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    List<Future<Key>> rotations = IntStream.range(0, count).mapToObj(ignored -> environment.pyrite().cryptography
        .rotateKey(RotateKeyRequest.newBuilder().setSubject(ANNE).setName(key).build())).toList();
    Future.join(rotations).transform(ignored -> Future.succeededFuture()).await(TIMEOUT);
    List<String> names = new ArrayList<>(List.of("keyVersions/" + key.substring(key.indexOf('/') + 1) + "-1"));
    for (Future<Key> rotation : rotations) {
      if (rotation.succeeded()) {
        names.add(rotation.result().getPrimaryVersion());
      } else {
        assertThat(rotation.cause()).isInstanceOfSatisfying(InvalidStatusException.class,
            failure -> assertThat(failure.actualStatus()).isEqualTo(GrpcStatus.ABORTED));
      }
    }
    assertThat(names).as("versions of the rotations that succeeded").hasSizeGreaterThan(1).doesNotHaveDuplicates();
    assertThat(environment.pyrite().cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder()
        .setSubject(ANNE).setKey(key).build()).await(TIMEOUT).getVersionsList()).extracting(KeyVersion::getName)
        .containsExactlyInAnyOrderElementsOf(names);
  }
}
