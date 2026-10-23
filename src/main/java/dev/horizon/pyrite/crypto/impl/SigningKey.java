package dev.horizon.pyrite.crypto.impl;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import io.vertx.core.Future;

/**
 * A key that signs: HMAC-SHA256 for the purpose {@code MAC}, or RS256, ES256 or EdDSA for {@code SIGNING}.
 *
 * <p>
 * A signature that does not verify is a rejection in the response, not a failure.
 */
public non-sealed interface SigningKey extends Key {

  /**
   * Signs data with the primary version.
   *
   * @param data the data
   * @return the signature and the version's name
   */
  Future<SignResponse> sign(byte[] data);

  /**
   * Verifies a signature with one version, or with each enabled version.
   *
   * @param data the data
   * @param signature the signature
   * @param version the version's name; empty to try each enabled version
   * @return the verdict
   */
  Future<VerifyResponse> verify(byte[] data, byte[] signature, String version);

  /**
   * Signs claims as a JWT in compact serialization. The header's key id is the primary version's id.
   *
   * @param type the JWT's {@code typ}; empty for {@code JWT}
   * @param claims the claims
   * @return the token and the version's name
   */
  Future<SignResponse> jwt(String type, Struct claims);

  /**
   * Verifies a JWT: its header, its signature with the version that its key id names, and its claims against what is
   * expected. A token without {@code exp} is rejected.
   *
   * @param token the token
   * @param expected the type, the issuer, the audience and the leeway
   * @return the verdict, with the claims when it is verified
   */
  Future<VerifyResponse> verify(String token, Expected expected);
}
