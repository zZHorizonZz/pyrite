package dev.horizon.pyrite.key;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.Verified;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.text.ParseException;
import java.time.Instant;
import java.util.Optional;

/**
 * A JWT in compact serialization that a verification reads: its header, which it checks before the signature, and its
 * claims, which it checks after.
 *
 * @param jws the token
 */
record Jwt(JWSObject jws) {

  static final String TYPE = "JWT";

  static Optional<Jwt> parse(String token) {
    try {
      return Optional.of(new Jwt(JWSObject.parse(token)));
    } catch (ParseException malformed) {
      return Optional.empty();
    }
  }

  Optional<Rejected> header(JWSAlgorithm algorithm, String type) {
    JWSHeader header = jws.getHeader();
    Rejected.Builder rejection = Rejected.newBuilder();
    if (header.getCriticalParams() != null && !header.getCriticalParams().isEmpty()) {
      rejection.setReason(Rejected.Reason.MALFORMED).setMessage("The token names critical header parameters, which"
          + " Pyrite does not support.");
    } else if (!algorithm.equals(header.getAlgorithm())) {
      rejection.setReason(Rejected.Reason.ALGORITHM).setMessage("The token is not signed with " + algorithm + ".");
    } else if (header.getType() == null || !type.equalsIgnoreCase(header.getType().getType())) {
      rejection.setReason(Rejected.Reason.TYPE).setMessage("The token is not of the type " + type + ".");
    } else if (header.getKeyID() == null) {
      rejection.setReason(Rejected.Reason.UNKNOWN_VERSION).setMessage("The token names no key id.");
    } else {
      return Optional.empty();
    }
    return Optional.of(rejection.build());
  }

  String keyId() {
    return jws.getHeader().getKeyID();
  }

  byte[] signingInput() {
    return jws.getSigningInput();
  }

  byte[] signature() {
    return jws.getSignature().decode();
  }

  VerifyResponse claims(Expected expectation, Instant now, String version) {
    JsonObject claims;
    try {
      claims = new JsonObject(jws.getPayload().toString());
    } catch (DecodeException | ClassCastException malformed) {
      return VerifyResponse.newBuilder().setRejected(Rejected.newBuilder().setReason(Rejected.Reason.MALFORMED)
          .setMessage("The claims of the token are not a JSON object.")).build();
    }
    Object audience = claims.getValue("aud");
    long seconds = now.getEpochSecond();
    long leeway = expectation.getLeeway().getSeconds();
    Rejected.Builder rejection = Rejected.newBuilder();
    if (!expectation.getIssuer().isEmpty() && !expectation.getIssuer().equals(claims.getValue("iss"))) {
      rejection.setReason(Rejected.Reason.ISSUER).setMessage("The token is not issued by " + expectation.getIssuer()
          + ".");
    } else if (!expectation.getAudience().isEmpty() && !(audience instanceof String single
        ? single.equals(expectation.getAudience())
        : audience instanceof JsonArray many && many.contains(expectation.getAudience()))) {
      rejection.setReason(Rejected.Reason.AUDIENCE).setMessage("The token is not for " + expectation.getAudience()
          + ".");
    } else if (!(claims.getValue("exp") instanceof Number expiry)) {
      rejection.setReason(Rejected.Reason.EXPIRED).setMessage("The token has no exp.");
    } else if (seconds > expiry.longValue() + leeway) {
      rejection.setReason(Rejected.Reason.EXPIRED).setMessage("The token expired at " + expiry + ".");
    } else if (claims.getValue("nbf") instanceof Number notBefore && seconds + leeway < notBefore.longValue()) {
      rejection.setReason(Rejected.Reason.NOT_YET_VALID).setMessage("The token is valid from " + notBefore + ".");
    } else {
      return VerifyResponse.newBuilder().setVerified(Verified.newBuilder().setClaims(new ClaimSet(claims).struct())
          .setKeyVersion(version)).build();
    }
    return VerifyResponse.newBuilder().setRejected(rejection).build();
  }
}
