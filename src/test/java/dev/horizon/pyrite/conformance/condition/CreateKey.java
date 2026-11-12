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
 * Creates a key of an algorithm under a parent, with a rotation period, as a subject, and leaves it.
 */
public record CreateKey(String subject, String parent, String id, Algorithm algorithm, Duration period)
    implements Condition {

  /**
   * Creates a key as anne, with no rotation period.
   */
  public CreateKey(String parent, String id, Algorithm algorithm) {
    this(ANNE, parent, id, algorithm, Duration.ZERO);
  }

  public CreateKey(String parent, String id, Algorithm algorithm, Duration period) {
    this(ANNE, parent, id, algorithm, period);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.put(Value.KEY, environment.pyrite().cryptography.createKey(CreateKeyRequest.newBuilder()
        .setSubject(subject).setKeyId(id).setParent(parent).setAlgorithm(algorithm).setRotationPeriod(
            com.google.protobuf.Duration.newBuilder().setSeconds(period.toSeconds()))
        .build()).await(TIMEOUT));
  }
}
