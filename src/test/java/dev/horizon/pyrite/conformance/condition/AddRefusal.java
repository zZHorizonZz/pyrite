package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import io.vertx.grpc.common.GrpcStatus;

/**
 * Adds an interceptor of a moment that fails each exchange with a status.
 */
public record AddRefusal(Moment moment, GrpcStatus status) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    moment.add(environment.pyrite().service, context -> context.fail(new PyriteException(status,
        "The interceptor refuses the exchange.")));
  }
}
