package dev.horizon.pyrite.internal;

import dev.horizon.pyrite.Encryption;
import dev.horizon.pyrite.Mac;
import dev.horizon.pyrite.Purpose;
import dev.horizon.pyrite.Signing;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Optional;

/**
 * The material of a version that runs in the process, and the operations it runs. A variant runs only the operations of
 * its purpose; the keyring never asks it for another.
 */
public sealed interface Material permits AesMaterial, HmacMaterial, KeyPairMaterial, PublicKeyMaterial {

  /**
   * @param secret the secret half as {@link #secret()} gives it, or empty for a public key alone
   * @param subjectPublicKeyInfo the public half of a signing version, as {@link #subjectPublicKeyInfo()} gives it
   * @return the material of a version of {@code purpose}, made again from what was stored of it
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
   * @return the secret half, to store: the AES or HMAC key, or the PKCS#8 private key; none for a public key alone
   */
  Optional<byte[]> secret();

  /**
   * @return the X.509 SubjectPublicKeyInfo of signing material, to store; none for an AES or HMAC key
   */
  Optional<byte[]> subjectPublicKeyInfo();

  /**
   * @return the ciphertext of {@code plaintext}, authenticating {@code context}
   */
  default byte[] encrypt(byte[] plaintext, byte[] context) {
    throw new IllegalStateException("This material does not encrypt");
  }

  /**
   * @return the plaintext of {@code ciphertext}
   * @throws dev.horizon.pyrite.common.FailedPreconditionException with the reason {@code UNREADABLE} when the
   * ciphertext was not made by this material under
   * {@code context}
   */
  default byte[] decrypt(byte[] ciphertext, byte[] context) {
    throw new IllegalStateException("This material does not decrypt");
  }

  /**
   * @return the HMAC-SHA256 of {@code data}
   */
  default byte[] mac(byte[] data) {
    throw new IllegalStateException("This material does not MAC");
  }

  /**
   * @return the signature of {@code data}, as JWS writes it
   */
  default byte[] sign(byte[] data) {
    throw new IllegalStateException("This material does not sign");
  }

  /**
   * @return the public half of signing material
   */
  default PublicKey publicKey() {
    throw new IllegalStateException("This material has no public key");
  }
}
