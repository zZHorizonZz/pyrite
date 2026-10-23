package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.Optional;

/**
 * A session of an account, of the type {@code pyrite.dev/Session}, which its token authenticates.
 *
 * <p>
 * Pyrite stores only a MAC of the token's secret. A session ends at its expiry time, when it idles out, or when it is
 * ended or deleted.
 */
public interface Session extends Resource {

  /**
   * Gives the session's token, which only a session that was just issued carries.
   *
   * @return the token, {@code <id>.<secret>}; empty for a session that was read
   */
  Optional<String> token();

  /**
   * Gives the time at which the session ends, however often it is used.
   *
   * @return the time
   */
  Instant expireTime();

  /**
   * Tells whether the session is live: not deleted, not expired, and not idle for longer than {@code idleLifetime}.
   *
   * @return whether it is live
   */
  boolean isLive();

  /**
   * Authenticates the secret of the session's token, and extends the session's idle expiry, up to its expiry time.
   *
   * @param secret the secret of the token
   * @return the principal of the session's account, or a failure with {@code UNAUTHENTICATED}
   */
  Future<Principal> authenticate(String secret);

  /**
   * Ends the session.
   *
   * @return when ended
   */
  Future<Void> end();
}
