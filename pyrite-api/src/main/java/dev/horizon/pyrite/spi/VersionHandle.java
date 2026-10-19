package dev.horizon.pyrite.spi;

import dev.horizon.pyrite.Origin;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.common.Revision;
import io.vertx.core.Future;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Optional;

/**
 * A version as its provider holds it, and the operations of its material. The keyring calls only the operations of the
 * key's purpose, and only on a version whose state allows them.
 */
public interface VersionHandle {

  /**
   * @return the id of the version, unique in its key; the provider chooses it, and it never changes
   */
  String id();

  State state();

  Origin origin();

  Instant created();

  /**
   * @return the self-signed X.509 certificate of a signing version, in DER, once the keyring has stored one
   */
  Optional<byte[]> certificate();

  /**
   * @return the version in {@code state}; it fails with {@code AbortedException} when its key is no longer at
   * {@code expected}. Moving to {@link State.Destroyed} destroys the material.
   */
  Future<VersionHandle> state(State state, Revision expected);

  /**
   * Stores the certificate the keyring issued for this version, signed by the version itself.
   *
   * @return the version with {@code certificate}; it fails with {@code AbortedException} when its key is no longer at
   * {@code expected}
   */
  Future<VersionHandle> certificate(byte[] certificate, Revision expected);

  /**
   * @return the ciphertext of {@code plaintext}, authenticating {@code context}, in the provider's own format
   */
  Future<byte[]> encrypt(byte[] plaintext, byte[] context);

  /**
   * @return the plaintext; it fails with {@code FailedPreconditionException}, {@link dev.horizon.pyrite.Reason#UNREADABLE}, when the
   * ciphertext was not made by this version under {@code context}
   */
  Future<byte[]> decrypt(byte[] ciphertext, byte[] context);

  /**
   * @return the HMAC-SHA256 of {@code data}
   */
  Future<byte[]> mac(byte[] data);

  /**
   * @return the signature of {@code data} as JWS writes it for the key's algorithm: PKCS#1 v1.5 for RS256, the 64
   * bytes of R and S for ES256, and Ed25519's own for EdDSA
   */
  Future<byte[]> sign(byte[] data);

  /**
   * @return the public half of a signing version
   */
  Future<PublicKey> publicKey();
}
