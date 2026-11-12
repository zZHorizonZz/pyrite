package dev.horizon.pyrite.key;

import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.util.Base64URL;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.crypto.v1.Verified;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import dev.horizon.pyrite.schema.keys.v1.Tables;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A key that signs: HMAC-SHA256 for the purpose {@code MAC}, or RS256, ES256 or EdDSA for {@code SIGNING}.
 *
 * <p>
 * A signature that does not verify is a rejection in the response, not a failure. A JWT's {@code alg} is the key's
 * algorithm, such as {@code HS256} for {@code HMAC_SHA256}, and its key id is the id of the version that signed, such
 * as {@code oauth-1}.
 */
public final class SigningKey extends Key {

  SigningKey(KeyType type, Tables.Key row) {
    super(type, row);
  }

  /**
   * Signs data with the primary version, after rotating the key when its rotation is due.
   *
   * @return the signature and the version's name
   */
  public Future<SignResponse> sign(byte[] data) {
    return primary().map(version -> SignResponse.newBuilder().setSignature(ByteString.copyFrom(version.sign(data)))
        .setKeyVersion(version.name()).build());
  }

  /**
   * Verifies a signature with one version, or with each enabled version.
   *
   * @param version the version's name; empty to try each enabled version
   * @return the verdict: verified, with the version that made the signature, or rejected with
   * {@code UNKNOWN_VERSION} for a version that the key does not have, {@code DISABLED_VERSION} for a disabled one,
   * and {@code SIGNATURE} when no version made the signature
   */
  public Future<VerifyResponse> verify(byte[] data, byte[] signature, String version) {
    return versions().map(found -> {
      List<KeyVersion> candidates = version.isEmpty() ? found.stream().filter(KeyVersion::isUsable).toList()
          : found.stream().filter(candidate -> candidate.name().equals(version)).toList();
      Rejected.Builder rejected = Rejected.newBuilder();
      if (!version.isEmpty() && candidates.isEmpty()) {
        rejected.setReason(Rejected.Reason.UNKNOWN_VERSION).setMessage("The key has no version " + version + ".");
      } else if (!version.isEmpty() && !candidates.getFirst().isUsable()) {
        rejected.setReason(Rejected.Reason.DISABLED_VERSION).setMessage("The version " + version + " is disabled.");
      } else {
        for (KeyVersion candidate : candidates) {
          if (candidate.verifies(data, signature)) {
            return VerifyResponse.newBuilder().setVerified(Verified.newBuilder().setKeyVersion(candidate.name()))
                .build();
          }
        }
        rejected.setReason(Rejected.Reason.SIGNATURE).setMessage("No version of the key made the signature.");
      }
      return VerifyResponse.newBuilder().setRejected(rejected).build();
    });
  }

  /**
   * Signs claims as a JWT in compact serialization. The header's key id is the primary version's id.
   *
   * @param type the JWT's {@code typ}; empty for {@code JWT}
   * @return the token and the version's name
   */
  public Future<SignResponse> jwt(String type, Struct claims) {
    return primary().map(version -> {
      JWSObject draft = new JWSObject(new JWSHeader.Builder(algorithm().jose())
          .type(new JOSEObjectType(type.isEmpty() ? Jwt.TYPE : type)).keyID(version.id()).build(),
          new Payload(ClaimSet.of(claims).json().encode()));
      String token = draft.getHeader().toBase64URL() + "." + draft.getPayload().toBase64URL() + "."
          + Base64URL.encode(version.sign(draft.getSigningInput()));
      return SignResponse.newBuilder().setToken(token).setKeyVersion(version.name()).build();
    });
  }

  /**
   * Verifies a JWT: its header, its signature with the version that its key id names, and its claims against what is
   * expected. A token without {@code exp} is rejected. Without a leeway, the clock skew of the options is the leeway.
   *
   * @param expected the type, the issuer, the audience and the leeway
   * @return the verdict: verified, with the claims, or rejected with the first reason that the token fails, checking
   * its header, then its signature, then its claims
   */
  public Future<VerifyResponse> verify(String token, Expected expected) {
    Optional<Jwt> parsed = Jwt.parse(token);
    if (parsed.isEmpty()) {
      return Future.succeededFuture(VerifyResponse.newBuilder().setRejected(Rejected.newBuilder()
          .setReason(Rejected.Reason.MALFORMED).setMessage("The token is not a JWS in compact serialization."))
          .build());
    }
    Jwt jwt = parsed.get();
    Optional<Rejected> header = jwt.header(algorithm().jose(), expected.getType().isEmpty() ? Jwt.TYPE
        : expected.getType());
    if (header.isPresent()) {
      return Future.succeededFuture(VerifyResponse.newBuilder().setRejected(header.get()).build());
    }
    String version = KeyVersionName.PREFIX + jwt.keyId();
    Expected leeway = expected.hasLeeway() ? expected
        : expected.toBuilder().setLeeway(com.google.protobuf.Duration
            .newBuilder().setSeconds(type().options().getClockSkew().toSeconds())).build();
    return verify(jwt.signingInput(), jwt.signature(), version).map(verdict -> verdict.hasRejected() ? verdict
        : jwt.claims(leeway, Instant.now(), version));
  }
}
