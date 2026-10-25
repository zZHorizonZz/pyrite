package dev.horizon.pyrite.crypto.impl;

import io.vertx.core.Future;

/**
 * A version of a key: its material, encrypted by the root key, and its state. A disabled version neither decrypts nor
 * verifies.
 */
public interface KeyVersion {

  /**
   * Gives the version's name.
   *
   * @return the name, such as {@code keyVersions/payments-2}
   */
  String name();

  /**
   * Gives the version's id, which a ciphertext and a JWT's key id name.
   *
   * @return the id, such as {@code payments-2}
   */
  String id();

  /**
   * Gives the version's number among the versions of its key.
   *
   * @return the number, from 1
   */
  long number();

  /**
   * Tells whether the version decrypts and verifies.
   *
   * @return true when it is enabled
   */
  boolean isUsable();

  /**
   * Gives the version's key.
   *
   * @return the key
   */
  Future<Key> key();

  /**
   * Enables or disables the version. The primary version is not disabled.
   *
   * @param enabled true to enable it
   * @param revision the revision that the change is made from
   * @return the changed version
   */
  Future<KeyVersion> update(boolean enabled, long revision);

  /**
   * Destroys the version. The primary version is not destroyed.
   *
   * @return a future that completes once the version is gone
   */
  Future<Void> delete();

  /**
   * Gives the version as a message.
   *
   * @return the message of {@code pyrite.crypto.v1}
   */
  dev.horizon.pyrite.crypto.v1.KeyVersion message();
}
