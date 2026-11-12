package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.CompletionHandler;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Removes the completion handler, which does the steps that a flow declares, from the end of flows.
 */
public record RemoveCompletion() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    environment.pyrite().service.removeFlowEndInterceptor(CompletionHandler.create());
  }
}
