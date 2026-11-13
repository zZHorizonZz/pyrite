package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Set;

/**
 * Calls a step that must fail with a status, or with one of a few.
 */
public record ExpectStatus(Set<GrpcStatus> statuses, Condition call) implements Condition {

  public ExpectStatus(GrpcStatus status, Condition call) {
    this(Set.of(status), call);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    try {
      call.evaluate(environment);
    } catch (InvalidStatusException failure) {
      if (!statuses.contains(failure.actualStatus())) {
        throw new AssertionError(call + " failed with " + failure.actualStatus() + ", not " + statuses, failure);
      }
      return;
    }
    throw new AssertionError(call + " did not fail; it fails with " + statuses);
  }
}
