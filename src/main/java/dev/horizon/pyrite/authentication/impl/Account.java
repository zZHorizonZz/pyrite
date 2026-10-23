package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;
import java.util.List;

/**
 * An account of the type {@code pyrite.dev/Account}. Its principal is {@code user:<id>}, so {@code accounts/anne} is
 * {@code user:anne}.
 *
 * <p>
 * Beneath an account are its identifiers, its factors and its sessions.
 */
public interface Account extends Resource {

  /**
   * Gives the account's principal.
   *
   * @return the principal, such as {@code user:anne}
   */
  String subject();

  /**
   * Reads the account's identifiers.
   *
   * @return the identifiers
   */
  Future<List<Identifier>> identifiers();

  /**
   * Reads the account's factors: active, pending and removed.
   *
   * @return the factors
   */
  Future<List<Factor>> factors();

  /**
   * Issues a session. The returned session carries its token, which Pyrite does not store and gives only once.
   *
   * @return the session
   */
  Future<Session> createSession();

  /**
   * Reads the account's sessions: live and ended.
   *
   * @return the sessions
   */
  Future<List<Session>> sessions();

  /**
   * Ends every live session of the account.
   *
   * @return when the sessions are ended
   */
  Future<Void> deleteSessions();
}
