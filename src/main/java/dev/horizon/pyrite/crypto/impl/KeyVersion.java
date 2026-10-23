package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.impl.resource.Resource;
import io.vertx.core.Future;

/**
 * A version of a key, of the type {@code pyrite.dev/KeyVersion}. Its material is encrypted by the root key in the field
 * {@code sealed}, and a signing version publishes its public key as a JWK.
 */
public interface KeyVersion extends Resource {

  /**
   * Gives the version's number: {@code 1} for the first version, one more for each rotation.
   *
   * @return the number
   */
  long number();

  /**
   * Tells whether the version is enabled and not deleted, so that it decrypts and verifies.
   *
   * @return whether it can be used
   */
  boolean isUsable();

  /**
   * Reads the version's key.
   *
   * @return the key
   */
  Future<Key> key();
}
