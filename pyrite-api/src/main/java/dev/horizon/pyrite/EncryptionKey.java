package dev.horizon.pyrite;

import io.vertx.core.Future;

/**
 * A key that encrypts data with AES-256-GCM.
 */
public non-sealed interface EncryptionKey extends Key {

  @Override
  Encryption purpose();

  /**
   * Encrypts with the primary version. The ciphertext contains the number of that version.
   *
   * @param context what the plaintext belongs to, such as {@code accounts/42}. It is authenticated but not stored, and
   * {@link #decrypt} requires the same context.
   */
  Future<byte[]> encrypt(byte[] plaintext, String context);

  /**
   * Decrypts with the version whose number the ciphertext contains.
   *
   * <p>
   * The future fails with {@code FailedPreconditionException} and the reason {@link Reason#UNREADABLE} if the
   * ciphertext was not made by this key under the context or was changed, and with the reason {@link Reason#VERSION} if
   * the version is not live.
   */
  Future<byte[]> decrypt(byte[] ciphertext, String context);
}
