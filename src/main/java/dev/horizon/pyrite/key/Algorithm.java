package dev.horizon.pyrite.key;

import com.nimbusds.jose.JWSAlgorithm;
import dev.horizon.pyrite.schema.keys.v1.Tables.Key.Purpose;
import java.security.KeyPair;
import java.security.SecureRandom;

/**
 * The algorithm of a key, which decides its purpose, the material that its versions hold, and the {@code alg} of its
 * JWTs. An encryption key signs no JWT, so {@code AES256_GCM} has no {@code alg}.
 */
enum Algorithm {
  AES256_GCM(Purpose.ENCRYPTION, null, null),
  HMAC_SHA256(Purpose.MAC, null, JWSAlgorithm.HS256),
  RS256(Purpose.SIGNING, Scheme.RS256, JWSAlgorithm.RS256),
  ES256(Purpose.SIGNING, Scheme.ES256, JWSAlgorithm.ES256),
  EDDSA(Purpose.SIGNING, Scheme.EDDSA, JWSAlgorithm.EdDSA);

  private static final int SECRET_KEY_BYTES = 32;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final Purpose purpose;
  private final Scheme scheme;
  private final JWSAlgorithm jose;

  Algorithm(Purpose purpose, Scheme scheme, JWSAlgorithm jose) {
    this.purpose = purpose;
    this.scheme = scheme;
    this.jose = jose;
  }

  /**
   * Generates the material of a new version.
   */
  Material generate() {
    return switch (this) {
      case AES256_GCM -> new Material.Aes(secretKey());
      case HMAC_SHA256 -> new Material.Hmac(secretKey());
      case RS256, ES256, EDDSA -> new Material.Pair(scheme, scheme.generate());
    };
  }

  /**
   * Restores the material of a version from its secret, and its public key when it has one.
   *
   * @param publicKeyInfo the public key as X.509 SubjectPublicKeyInfo; null for a secret key
   */
  Material restore(byte[] secret, byte[] publicKeyInfo) {
    return switch (this) {
      case AES256_GCM -> new Material.Aes(secret);
      case HMAC_SHA256 -> new Material.Hmac(secret);
      case RS256, ES256, EDDSA -> new Material.Pair(scheme, new KeyPair(scheme.publicKey(publicKeyInfo),
          scheme.privateKey(secret)));
    };
  }

  JWSAlgorithm jose() {
    return jose;
  }

  public Purpose purpose() {
    return purpose;
  }

  Scheme scheme() {
    return scheme;
  }

  private static byte[] secretKey() {
    byte[] key = new byte[SECRET_KEY_BYTES];
    RANDOM.nextBytes(key);
    return key;
  }
}
