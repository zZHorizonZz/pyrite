package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Signing;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Optional;

/**
 * The material of a version whose operations run in the process. Each variant implements the operations of one
 * purpose only.
 */
public sealed interface Material permits AesMaterial, HmacMaterial, KeyPairMaterial, PublicKeyMaterial {

  /**
   * Restores the material of a version from what was stored.
   *
   * @param secret the value of {@link #secret()}, or an empty array for a public key only
   * @param subjectPublicKeyInfo the value of {@link #subjectPublicKeyInfo()} for a signing version
   */
  static Material of(Purpose<?> purpose, byte[] secret, byte[] subjectPublicKeyInfo) {
    return switch (purpose) {
      case Encryption encryption -> new AesMaterial(secret);
      case Mac mac -> new HmacMaterial(secret);
      case Signing signing -> {
        Scheme scheme = Scheme.valueOf(signing.algorithm().name());
        PublicKey publicKey = scheme.publicKey(subjectPublicKeyInfo);
        yield secret.length == 0
            ? new PublicKeyMaterial(scheme, publicKey)
            : new KeyPairMaterial(scheme, new KeyPair(publicKey, scheme.privateKey(secret)));
      }
    };
  }

  /**
   * Returns the secret to store: the AES or HMAC key, or the PKCS#8 private key. Returns nothing for a public key only.
   */
  Optional<byte[]> secret();

  /**
   * Returns the X.509 SubjectPublicKeyInfo of a signing version. Returns nothing for an AES or HMAC key.
   */
  Optional<byte[]> subjectPublicKeyInfo();

  /**
   * Encrypts the plaintext and authenticates the context.
   */
  default byte[] encrypt(byte[] plaintext, byte[] context) {
    throw new IllegalStateException("This material does not encrypt");
  }

  /**
   * Decrypts the ciphertext.
   *
   * @throws dev.horizon.pyrite.common.FailedPreconditionException with the reason {@code UNREADABLE} if this material
   * did not make the ciphertext under the context
   */
  default byte[] decrypt(byte[] ciphertext, byte[] context) {
    throw new IllegalStateException("This material does not decrypt");
  }

  /**
   * Returns the HMAC-SHA256 of the data.
   */
  default byte[] mac(byte[] data) {
    throw new IllegalStateException("This material does not MAC");
  }

  /**
   * Signs the data, and returns the signature in the format of JWS.
   */
  default byte[] sign(byte[] data) {
    throw new IllegalStateException("This material does not sign");
  }

  /**
   * Returns the public key of a signing version.
   */
  default PublicKey publicKey() {
    throw new IllegalStateException("This material has no public key");
  }
}
