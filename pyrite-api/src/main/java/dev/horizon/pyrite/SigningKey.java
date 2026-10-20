package dev.horizon.pyrite;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.security.PublicKey;

/**
 * A key that signs JWTs and bytes, and verifies them. A pending version is published before it signs, and a version
 * stays published until it expires or is disabled.
 */
public non-sealed interface SigningKey extends Key {

  @Override
  Signing purpose();

  /**
   * Signs a JWT whose {@code typ} is {@code JWT}.
   *
   * @see #jwt(String, JsonObject)
   */
  Future<String> jwt(JsonObject claims);

  /**
   * Signs a JWT with the primary version. The header has the algorithm of the key as {@code alg} and the id of the
   * version as {@code kid}. Pyrite adds no claims.
   *
   * @param type the {@code typ} of the JWT, such as {@code JWT} or {@code at+jwt}
   * @return the JWT in compact serialization
   */
  Future<String> jwt(String type, JsonObject claims);

  /**
   * Signs the data exactly as given, with the primary version.
   *
   * <p>
   * Do not use a key that signs JWTs to sign data that someone else chose. A JWT is signed over its encoded header
   * and claims, so a signature over such data can be a valid JWT. See RFC 8725, section 3.12.
   */
  Future<Signature> sign(byte[] data);

  /**
   * Verifies a JWT with the live versions of this key. Checks the signature, {@code alg}, {@code typ}, {@code iss},
   * {@code aud}, {@code exp} and {@code nbf}. The future fails only if the key cannot be read.
   *
   * @return {@code Verified} with the claims and the version that signed, or {@code Rejected} with the reason
   */
  Future<Verification> verify(String token, Expected expected);

  /**
   * Verifies a signature over data with the live versions of this key.
   *
   * @return {@code Verified} with the version and no claims, or {@code Rejected} with the reason
   */
  Future<Verification> verify(byte[] data, Signature signature);

  /**
   * Returns the JWK set of every live version of this key.
   */
  Future<JsonObject> published();

  /**
   * Creates a version and returns its private key once, in PKCS#8. The provider stores only the public key. The version
   * is live at once, never becomes primary, and expires according to the validity of the key.
   *
   * <p>
   * The holder of the private key can sign any JWT that this key verifies. Use the key for one principal only, such
   * as one service account. See RFC 8725, section 3.8.
   *
   * <p>
   * The future fails with {@code UnimplementedException} if the provider does not release private keys.
   */
  Future<Export<Version>> export();

  /**
   * Adds a version for a public key that its holder created. The version is live at once, never becomes primary, and
   * expires according to the validity of the key.
   *
   * <p>
   * The future fails with {@code InvalidArgumentException} if the public key does not match the algorithm of the key,
   * and with {@code UnimplementedException} if the provider does not accept public keys.
   */
  Future<Version> upload(PublicKey publicKey);
}
