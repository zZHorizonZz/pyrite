package dev.horizon.pyrite.crypto.impl;

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
    Rejected.Builder rejected = Rejected.newBuilder();
    if (header.getCriticalParams() != null && !header.getCriticalParams().isEmpty()) {
      rejected.setReason(Rejected.Reason.MALFORMED).setMessage("The token names critical header parameters, which"
          + " Pyrite does not support.");
    } else if (!algorithm.equals(header.getAlgorithm())) {
      rejected.setReason(Rejected.Reason.ALGORITHM).setMessage("The token is not signed with " + algorithm + ".");
    } else if (header.getType() == null || !type.equalsIgnoreCase(header.getType().getType())) {
      rejected.setReason(Rejected.Reason.TYPE).setMessage("The token is not of the type " + type + ".");
    } else if (header.getKeyID() == null) {
      rejected.setReason(Rejected.Reason.UNKNOWN_VERSION).setMessage("The token names no key id.");
    } else {
      return Optional.empty();
    }
    return Optional.of(rejected.build());
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

  VerifyResponse claims(Expected expected, Instant now, String version) {
    JsonObject claims;
    try {
      claims = new JsonObject(jws.getPayload().toString());
    } catch (DecodeException | ClassCastException malformed) {
      return VerifyResponse.newBuilder().setRejected(Rejected.newBuilder().setReason(Rejected.Reason.MALFORMED)
          .setMessage("The claims of the token are not a JSON object.")).build();
    }
    Object audience = claims.getValue("aud");
    long seconds = now.getEpochSecond();
    long leeway = expected.getLeeway().getSeconds();
    Rejected.Builder rejected = Rejected.newBuilder();
    if (!expected.getIssuer().isEmpty() && !expected.getIssuer().equals(claims.getValue("iss"))) {
      rejected.setReason(Rejected.Reason.ISSUER).setMessage("The token is not issued by " + expected.getIssuer()
          + ".");
    } else if (!expected.getAudience().isEmpty() && !(audience instanceof String single
        ? single.equals(expected.getAudience())
        : audience instanceof JsonArray many && many.contains(expected.getAudience()))) {
      rejected.setReason(Rejected.Reason.AUDIENCE).setMessage("The token is not for " + expected.getAudience()
          + ".");
    } else if (!(claims.getValue("exp") instanceof Number expires)) {
      rejected.setReason(Rejected.Reason.EXPIRED).setMessage("The token has no exp.");
    } else if (seconds > expires.longValue() + leeway) {
      rejected.setReason(Rejected.Reason.EXPIRED).setMessage("The token expired at " + expires + ".");
    } else if (claims.getValue("nbf") instanceof Number notBefore && seconds + leeway < notBefore.longValue()) {
      rejected.setReason(Rejected.Reason.NOT_YET_VALID).setMessage("The token is valid from " + notBefore + ".");
    } else {
      return VerifyResponse.newBuilder().setVerified(Verified.newBuilder().setClaims(new Claims(claims).struct())
          .setKeyVersion(version)).build();
    }
    return VerifyResponse.newBuilder().setRejected(rejected).build();
  }
}
