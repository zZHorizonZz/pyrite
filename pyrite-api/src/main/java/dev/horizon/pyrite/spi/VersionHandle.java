package dev.horizon.pyrite.spi;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.Revision;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;

/**
 * A version as its provider stores it, and the operations of its material. The keyring calls only the operations of
 * the purpose of the key, and only on a version whose state allows them.
 */
public interface VersionHandle {

  /**
   * Returns the id of the version. The provider chooses it, and it never changes.
   */
  String id();

  State state();

  Origin origin();

  Instant created();

  /**
   * Returns when the version expires, or {@link Instant#MAX} if it does not expire.
   */
  Instant expires();

  /**
   * Changes the state of the version. Changing it to {@link State.Destroyed} deletes the material. The future fails
   * with {@code AbortedException} if the key changed after {@code expected}.
   */
  Future<VersionHandle> state(State state, Revision expected);

  /**
   * Encrypts the plaintext and authenticates the context, in a format that the provider chooses.
   */
  Future<byte[]> encrypt(byte[] plaintext, byte[] context);

  /**
   * Decrypts the ciphertext. The future fails with {@code FailedPreconditionException} and the reason
   * {@link dev.horizon.pyrite.Reason#UNREADABLE} if this version did not make the ciphertext under the context.
   */
  Future<byte[]> decrypt(byte[] ciphertext, byte[] context);

  /**
   * Returns the HMAC-SHA256 of the data.
   */
  Future<byte[]> mac(byte[] data);

  /**
   * Signs the data, and returns the signature in the format of JWS: PKCS#1 v1.5 for RS256, the 64 bytes of R and S for
   * ES256, and the Ed25519 signature for EdDSA.
   */
  Future<byte[]> sign(byte[] data);

  /**
   * Returns the public key of a signing version.
   */
  Future<PublicKey> publicKey();
}
