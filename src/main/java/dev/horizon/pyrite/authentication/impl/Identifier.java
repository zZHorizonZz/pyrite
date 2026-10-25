package dev.horizon.pyrite.authentication.impl;

import io.vertx.core.Future;

/**
 * An identifier of an account, of a kind that the declaration names, such as an email address, normalized as its
 * kind says. A kind that receives codes takes codes, which the application sends.
 */
public interface Identifier {

  /**
   * Gives the identifier's name.
   *
   * @return the name, such as {@code identifiers/0b0d...}
   */
  String name();

  /**
   * Gives the name of the identifier's account.
   *
   * @return the name, such as {@code accounts/anne}
   */
  String account();

  /**
   * Gives the identifier's kind.
   *
   * @return the kind, as the declaration names it, such as {@code email}
   */
  String kind();

  /**
   * Gives the identifier's value.
   *
   * @return the value, as its kind normalizes it
   */
  String value();

  boolean isVerified();

  /**
   * Tells whether a code can be sent to the identifier.
   *
   * @return true when its kind receives codes and it is not removed
   */
  boolean isDeliverable();

  /**
   * Marks the identifier verified.
   *
   * @return the verified identifier
   */
  Future<Identifier> verify();

  /**
   * Gives the identifier as a message.
   *
   * @return the message of {@code pyrite.authentication.v1}
   */
  dev.horizon.pyrite.authentication.v1.Identifier message();
}
