package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;

/**
 * A factor of an account, of the type {@code pyrite.dev/Factor}: a password, a TOTP secret, a passkey or a set of
 * recovery codes.
 *
 * <p>
 * An enrolment flow creates a factor as {@code PENDING}, and activates it when the flow completes. Only flows create
 * factors.
 */
public interface Factor extends Resource {

  /**
   * Tells whether the factor is active and not removed.
   *
   * @return whether it is active
   */
  boolean isActive();

  /**
   * Reads the factor's account.
   *
   * @return the account
   */
  Future<Account> account();

  /**
   * Activates a pending factor, and removes the active factor of the same kind that it replaces.
   *
   * @return the active factor
   */
  Future<Factor> activate();

  /**
   * Removes the factor.
   *
   * @return when removed
   */
  Future<Void> remove();
}
