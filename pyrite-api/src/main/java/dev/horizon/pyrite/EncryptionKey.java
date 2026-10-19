package dev.horizon.pyrite;

import io.vertx.core.Future;

/**
 * A key that encrypts what an application stores and decrypts it again. Its algorithm is AES-256-GCM.
 */
public non-sealed interface EncryptionKey extends Key {

  @Override
  Encryption purpose();

  /**
   * Encrypts with the primary version.
   *
   * @param context what the plaintext belongs to, such as {@code accounts/42}; it is authenticated and not stored,
   * and the ciphertext decrypts under the same context only
   * @return the ciphertext, which names the version that made it
   */
  Future<byte[]> encrypt(byte[] plaintext, String context);

  /**
   * Decrypts with the version the ciphertext names, which may be any live version.
   *
   * @return the plaintext; it fails with {@code FailedPreconditionException}, {@link Reason#UNREADABLE}, when the ciphertext was not made
   * by this key under {@code context} or was changed, and {@link Reason#VERSION} when the version it names is
   * disabled or destroyed
   */
  Future<byte[]> decrypt(byte[] ciphertext, String context);
}
