package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Adds an interceptor of codes that fails to send each with a status.
 */
public record AddCodeRefusal(GrpcStatus status) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.pyrite().service.addCodeInterceptor(context -> context.fail(new PyriteException(status,
        "The code cannot be sent.")));
  }
}
