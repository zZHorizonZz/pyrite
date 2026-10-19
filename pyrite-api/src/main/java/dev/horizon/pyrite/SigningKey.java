package dev.horizon.pyrite;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.security.PublicKey;
import java.util.Map;

/**
 * A key that signs tokens and bytes and verifies them. A new version is published {@link Signing#ahead()} before it
 * signs anything, and a retired version stays published for {@link Signing#kept()} after it stops.
 */
public non-sealed interface SigningKey extends Key {

  @Override
  Signing purpose();

  /**
   * Signs a JWT of type {@code JWT}.
   *
   * @see #jwt(String, JsonObject)
   */
  Future<String> jwt(JsonObject claims);

  /**
   * Signs a JWT with the primary version, its id as {@code kid} and the algorithm of the key as {@code alg}. The claims
   * are written as they are given; Pyrite adds none.
   *
   * @param type the {@code typ} of the token, such as {@code JWT} or {@code at+jwt}
   * @return the compact serialization of the token
   */
  Future<String> jwt(String type, JsonObject claims);

  /**
   * @return the signature of {@code data} under the primary version, naming that version
   */
  Future<Signature> sign(byte[] data);

  /**
   * Verifies a JWT against the live versions of this key: its signature, its {@code typ}, {@code iss} and {@code aud},
   * and its {@code exp} and {@code nbf} within the leeway.
   *
   * @return {@code Verified} with the claims and the version that signed, or {@code Rejected} with the reason; the
   * future fails only when the key cannot be read
   */
  Future<Verification> verify(String token, Expected expected);

  /**
   * @return {@code Verified} with the version and no claims when a live version of this key made {@code signature}
   * over {@code data}, or {@code Rejected} with the reason
   */
  Future<Verification> verify(byte[] data, Signature signature);

  /**
   * @return the JWK set of every published version: pending, primary, and retired within {@link Signing#kept()}
   */
  Future<JsonObject> published();

  /**
   * @return the self-signed X.509 certificate of every published version that has one, in PEM, by version id
   */
  Future<Map<String, String>> certificates();

  /**
   * Makes a version whose private half is given to the caller once and not kept. It is live at once and never primary:
   * it verifies what its holder signs, as a client assertion of RFC 7523.
   *
   * @return the version and its private key; it fails with {@code UnimplementedException} when the
   * provider keeps every private key, as a cloud KMS does
   */
  Future<Export<Version>> export();

  /**
   * Adds a version for a public key that its holder made, live at once and never primary.
   *
   * @return the version; it fails with {@code InvalidArgumentException} when {@code publicKey} is not a key of the key's algorithm, and
   * with {@code UnimplementedException} when the provider does not take public keys
   */
  Future<Version> upload(PublicKey publicKey);
}
