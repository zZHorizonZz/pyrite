package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.FlowContext;
import dev.horizon.pyrite.Pyrite;
import io.vertx.core.Handler;

/**
 * A moment of flows that interceptors run at.
 */
public enum Moment {
  START,
  STAGE_END,
  END,
  FAILURE;

  void add(Pyrite pyrite, Handler<FlowContext> interceptor) {
    switch (this) {
      case START -> pyrite.addFlowStartInterceptor(interceptor);
      case STAGE_END -> pyrite.addStageEndInterceptor(interceptor);
      case END -> pyrite.addFlowEndInterceptor(interceptor);
      case FAILURE -> pyrite.addFlowFailureInterceptor(interceptor);
    }
  }

  void remove(Pyrite pyrite, Handler<FlowContext> interceptor) {
    switch (this) {
      case START -> pyrite.removeFlowStartInterceptor(interceptor);
      case STAGE_END -> pyrite.removeStageEndInterceptor(interceptor);
      case END -> pyrite.removeFlowEndInterceptor(interceptor);
      case FAILURE -> pyrite.removeFlowFailureInterceptor(interceptor);
    }
  }
}
