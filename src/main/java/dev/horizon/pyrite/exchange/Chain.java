package dev.horizon.pyrite.exchange;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A run of a chain of handlers. Each handler gets a context of its own {@link Turn}: it calls {@code next()} to pass to
 * the handler after it, or {@code fail(cause)} to end the run. A turn counts once, so a handler that calls both, or
 * either twice, moves the run once; and once the run ended, a turn does nothing. The run succeeds after the last
 * handler, and fails with the first failure, or with what a handler throws.
 *
 * @param <C> the context that the handlers take
 */
abstract class Chain<C> {

  private final List<Handler<C>> handlers;
  private final Promise<Void> promise = Promise.promise();

  Chain(List<Handler<C>> handlers) {
    this.handlers = List.copyOf(handlers);
  }

  /**
   * Runs the chain from its first handler.
   *
   * @return a future that completes after the last handler, or fails with the failure of a handler
   */
  Future<Void> run() {
    call(0);
    return promise.future();
  }

  /**
   * Gives the context of a handler, whose {@code next()} and {@code fail(cause)} are those of its turn.
   */
  abstract C context(Turn turn);

  private void call(int index) {
    if (index == handlers.size()) {
      promise.tryComplete();
      return;
    }
    Turn turn = new Turn(index);
    try {
      handlers.get(index).handle(context(turn));
    } catch (VirtualMachineError fatal) {
      throw fatal;
    } catch (Throwable failure) {
      promise.tryFail(failure);
    }
  }

  /**
   * The turn of one handler in the run.
   */
  final class Turn {

    private final int index;
    private final AtomicBoolean spent = new AtomicBoolean();

    private Turn(int index) {
      this.index = index;
    }

    /**
     * Passes the run to the next handler, the first time that the handler calls it, while the run goes on.
     */
    void next() {
      if (spent.compareAndSet(false, true) && !promise.future().isComplete()) {
        call(index + 1);
      }
    }

    /**
     * Ends the run with a failure, the first time that the handler calls it.
     */
    void fail(Throwable cause) {
      if (spent.compareAndSet(false, true)) {
        promise.tryFail(cause);
      }
    }
  }
}
