package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of the handlers that an application gives Pyrite: interceptors and observers.
 */
public final class HandlerPlan implements Plan {

  @Override
  public String name() {
    return "handlers";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(FlowEndInterceptor::new, FlowEndRefusal::new, CompletionRemoval::new,
        FlowStartRefusal::new, FlowFailureInterceptor::new, StageEndInterceptor::new, InterceptorRemoval::new, CodeRefusal::new,
        AccountObservation::new, SessionObservation::new, ThrowingHandler::new);
  }
}
