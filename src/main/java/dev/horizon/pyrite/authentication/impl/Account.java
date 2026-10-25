package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;
import java.util.List;

/**
 * An account, the resource {@code pyrite.dev/Account}: the identifiers that name it, the factors that prove it, and
 * the sessions that its flows issued. The account owns itself, as {@code user:<id>}.
 */
public interface Account {

  /**
   * Gives the account's name.
   *
   * @return the name, such as {@code accounts/anne}
   */
  String name();

  /**
   * Gives the subject that the account calls as.
   *
   * @return the subject, such as {@code user:anne}
   */
  String subject();

  /**
   * Gives the account as a resource, which grants and checks reach.
   *
   * @return the resource
   */
  Resource resource();

  /**
   * Gives the account's identifiers.
   *
   * @return the identifiers, by name
   */
  Future<List<Identifier>> identifiers();

  /**
   * Gives the account's factors, those that were removed among them.
   *
   * @return the factors, by name
   */
  Future<List<Factor>> factors();

  /**
   * Issues a session.
   *
   * @return the session, with its token
   */
  Future<Session> createSession();

  /**
   * Ends every session of the account.
   *
   * @return a future that completes once they are ended
   */
  Future<Void> deleteSessions();

  /**
   * Gives the account as a message.
   *
   * @return the message of {@code pyrite.authentication.v1}
   */
  dev.horizon.pyrite.authentication.v1.Account message();
}
