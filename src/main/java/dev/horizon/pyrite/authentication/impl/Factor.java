package dev.horizon.pyrite.authentication.impl;

import io.vertx.core.Future;

/**
 * A factor of an account: a password, an authenticator app, a passkey or recovery codes. A flow enrols a factor as
 * pending, and activates it once it is proven; activating one replaces the active factor of its kind.
 */
public interface Factor {

  /**
   * Gives the factor's name.
   *
   * @return the name, such as {@code factors/0b0d...}
   */
  String name();

  /**
   * Tells whether the factor proves the account.
   *
   * @return true when it is active and not removed
   */
  boolean isActive();

  /**
   * Gives the factor's account.
   *
   * @return the account
   */
  Future<Account> account();

  /**
   * Activates the factor, and removes the active factor of its kind.
   *
   * @return the active factor
   */
  Future<Factor> activate();

  /**
   * Removes the factor. The purge deletes it.
   *
   * @return a future that completes once it is removed
   */
  Future<Void> remove();

  /**
   * Gives the factor as a message.
   *
   * @return the message of {@code pyrite.authentication.v1}
   */
  dev.horizon.pyrite.authentication.v1.Factor message();
}
