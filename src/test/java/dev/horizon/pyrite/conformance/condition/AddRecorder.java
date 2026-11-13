package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.ExchangeContext;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Adds an interceptor of a moment that keeps each context that it sees, and leaves them.
 */
public record AddRecorder(Moment moment) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<ExchangeContext> contexts = new CopyOnWriteArrayList<>();
    environment.put(Value.INTERCEPTIONS, contexts);
    moment.add(environment.pyrite().service, context -> {
      contexts.add(context);
      context.next();
    });
  }
}
