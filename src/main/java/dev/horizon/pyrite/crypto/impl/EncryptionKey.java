package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.crypto.v1.DecryptResponse;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import io.vertx.core.Future;

/**
 * A key whose purpose is {@code ENCRYPTION}: AES-256 in GCM mode.
 *
 * <p>
 * A ciphertext names the version that made it, and is bound to the key's name and the caller's associated data. It
 * decrypts only with the same key and the same associated data.
 */
public non-sealed interface EncryptionKey extends Key {

  /**
   * Encrypts with the primary version.
   *
   * @param plaintext the plaintext
   * @param associatedData the data that a decryption names again, such as the id of the row that stores the
   * ciphertext
   * @return the ciphertext and the version's name
   */
  Future<EncryptResponse> encrypt(byte[] plaintext, byte[] associatedData);

  /**
   * Decrypts with the version that encrypted.
   *
   * @param ciphertext the ciphertext
   * @param associatedData the data that the encryption named
   * @return the plaintext and the version's name, or a failure with {@code FAILED_PRECONDITION} when the version is
   * disabled or deleted
   */
  Future<DecryptResponse> decrypt(byte[] ciphertext, byte[] associatedData);
}
