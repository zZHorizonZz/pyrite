package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Principal;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.Optional;

/**
 * A session of an account. Its token is shown once, when a flow issues it; Pyrite keeps only a MAC of its secret. A
 * session ends when it expires, when it is idle too long, or when it is ended.
 */
public interface Session {

  /**
   * Gives the session's name.
   *
   * @return the name, such as {@code sessions/0b0d...}
   */
  String name();

  /**
   * Gives the session's token, which only a session just issued has.
   *
   * @return the token, or empty
   */
  Optional<String> token();

  Instant expireTime();

  /**
   * Tells whether the session signs its account in.
   *
   * @return true when it has not ended
   */
  boolean isLive();

  /**
   * Gives the principal of the session, if a token's secret is the session's.
   *
   * @param secret the token's secret
   * @return the principal, or a failure with {@code UNAUTHENTICATED}
   */
  Future<Principal> authenticate(String secret);

  /**
   * Ends the session. The purge deletes it.
   *
   * @return a future that completes once it is ended
   */
  Future<Void> end();

  /**
   * Gives the session as a message.
   *
   * @return the message of {@code pyrite.authentication.v1}
   */
  dev.horizon.pyrite.authentication.v1.Session message();
}
