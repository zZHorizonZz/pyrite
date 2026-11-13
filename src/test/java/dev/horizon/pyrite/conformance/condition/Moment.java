package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.ExchangeContext;
import dev.horizon.pyrite.Pyrite;
import io.vertx.core.Handler;

/**
 * A moment of exchanges that interceptors run at.
 */
public enum Moment {
  START,
  STAGE_END,
  END,
  FAILURE;

  void add(Pyrite pyrite, Handler<ExchangeContext> interceptor) {
    switch (this) {
      case START -> pyrite.addExchangeStartInterceptor(interceptor);
      case STAGE_END -> pyrite.addStageEndInterceptor(interceptor);
      case END -> pyrite.addExchangeEndInterceptor(interceptor);
      case FAILURE -> pyrite.addExchangeFailureInterceptor(interceptor);
    }
  }

  void remove(Pyrite pyrite, Handler<ExchangeContext> interceptor) {
    switch (this) {
      case START -> pyrite.removeExchangeStartInterceptor(interceptor);
      case STAGE_END -> pyrite.removeStageEndInterceptor(interceptor);
      case END -> pyrite.removeExchangeEndInterceptor(interceptor);
      case FAILURE -> pyrite.removeExchangeFailureInterceptor(interceptor);
    }
  }
}
