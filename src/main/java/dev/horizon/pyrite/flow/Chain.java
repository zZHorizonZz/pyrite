package dev.horizon.pyrite.flow;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.List;

/**
 * A run of a chain of handlers, which is also the context that each handler gets: a handler calls {@link #next()} to
 * pass to the handler after it, or {@link #fail(Throwable)} to end the run. The run succeeds after the last handler.
 *
 * @param <C> the context that the handlers take, which this run is
 */
abstract class Chain<C> {

  private final List<Handler<C>> handlers;
  private final Promise<Void> promise = Promise.promise();
  private int index;

  Chain(List<Handler<C>> handlers) {
    this.handlers = List.copyOf(handlers);
  }

  /**
   * Runs the chain from its first handler.
   *
   * @return a future that completes after the last handler, or fails with the failure of a handler
   */
  Future<Void> run() {
    next();
    return promise.future();
  }

  public void next() {
    if (index == handlers.size()) {
      promise.tryComplete();
      return;
    }
    Handler<C> handler = handlers.get(index++);
    try {
      handler.handle(context());
    } catch (RuntimeException failure) {
      promise.tryFail(failure);
    }
  }

  public void fail(Throwable cause) {
    promise.tryFail(cause);
  }

  /**
   * Gives this run as the context of the handlers.
   */
  abstract C context();
}
