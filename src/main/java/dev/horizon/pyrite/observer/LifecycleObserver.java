package dev.horizon.pyrite.observer;

import io.vertx.core.Handler;

/**
 * The handlers that hear of the lifetime of a kind of resource: when one begins, such as an account that is created or
 * a session that is issued, and when one ends. A handler hears after the fact; one that throws is logged, and changes
 * nothing.
 *
 * @param <M> the message of the resource
 */
public final class LifecycleObserver<M> {

  private static final System.Logger LOGGER = System.getLogger(LifecycleObserver.class.getName());

  private final String resources;
  private volatile Handler<M> beginHandler;
  private volatile Handler<M> endHandler;

  /**
   * Creates an observer without handlers.
   *
   * @param resources what the observer hears of, for the log, such as {@code accounts}
   */
  public LifecycleObserver(String resources) {
    this.resources = resources;
  }

  public void beginHandler(Handler<M> handler) {
    this.beginHandler = handler;
  }

  public void endHandler(Handler<M> handler) {
    this.endHandler = handler;
  }

  /**
   * Tells the handler of beginnings of a resource.
   */
  public void begin(M resource) {
    tell(beginHandler, resource);
  }

  /**
   * Tells the handler of ends of a resource.
   */
  public void end(M resource) {
    tell(endHandler, resource);
  }

  private void tell(Handler<M> handler, M resource) {
    if (handler == null) {
      return;
    }
    try {
      handler.handle(resource);
    } catch (RuntimeException failure) {
      LOGGER.log(System.Logger.Level.ERROR, "A handler of " + resources + " failed", failure);
    }
  }
}
