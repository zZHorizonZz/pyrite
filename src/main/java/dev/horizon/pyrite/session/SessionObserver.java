package dev.horizon.pyrite.session;

import dev.horizon.pyrite.authentication.v1.Session;
import io.vertx.core.Handler;

/**
 * The handlers that observe sessions: one that Pyrite tells of each session that it issues, and one of each that it
 * ends. They hear of a session after the fact, so they cannot stop it; a session that expires, or whose account is
 * deleted, ends without them. A handler that throws changes nothing; the observer logs the throw.
 */
public final class SessionObserver {

  private static final System.Logger LOGGER = System.getLogger(SessionObserver.class.getName());

  private volatile Handler<Session> issueHandler;
  private volatile Handler<Session> endHandler;

  /**
   * Sets the handler of issued sessions, in place of the one before.
   *
   * @param handler the handler, or {@code null} for none
   */
  public void issueHandler(Handler<Session> handler) {
    this.issueHandler = handler;
  }

  /**
   * Sets the handler of ended sessions, in place of the one before.
   *
   * @param handler the handler, or {@code null} for none
   */
  public void endHandler(Handler<Session> handler) {
    this.endHandler = handler;
  }

  void issue(Session session) {
    tell(issueHandler, session);
  }

  void end(Session session) {
    tell(endHandler, session);
  }

  private static void tell(Handler<Session> handler, Session session) {
    if (handler == null) {
      return;
    }
    try {
      handler.handle(session);
    } catch (RuntimeException failure) {
      LOGGER.log(System.Logger.Level.ERROR, "A handler of sessions failed", failure);
    }
  }
}
