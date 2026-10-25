package dev.horizon.pyrite.crypto.impl;

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
import java.util.List;
import java.util.Optional;

final class SigningKeyImpl extends KeyImpl implements SigningKey {

  SigningKeyImpl(KeyTypeImpl type, Tables.Key row) {
    super(type, row);
  }

  @Override
  public Future<SignResponse> sign(byte[] data) {
    return primary().map(version -> SignResponse.newBuilder()
        .setSignature(ByteString.copyFrom(((KeyVersionImpl) version).sign(data))).setKeyVersion(version.name())
        .build());
  }

  @Override
  public Future<VerifyResponse> verify(byte[] data, byte[] signature, String named) {
    return versions().map(found -> {
      List<KeyVersion> candidates = named.isEmpty() ? found.stream().filter(KeyVersion::isUsable).toList()
          : found.stream().filter(version -> version.name().equals(named)).toList();
      Rejected.Builder rejected = Rejected.newBuilder();
      if (!named.isEmpty() && candidates.isEmpty()) {
        rejected.setReason(Rejected.Reason.UNKNOWN_VERSION).setMessage("The key has no version " + named + ".");
      } else if (!named.isEmpty() && !candidates.getFirst().isUsable()) {
        rejected.setReason(Rejected.Reason.DISABLED_VERSION).setMessage("The version " + named + " is disabled or"
            + " deleted.");
      } else {
        for (KeyVersion version : candidates) {
          if (((KeyVersionImpl) version).verifies(data, signature)) {
            return VerifyResponse.newBuilder().setVerified(Verified.newBuilder().setKeyVersion(version.name()))
                .build();
          }
        }
        rejected.setReason(Rejected.Reason.SIGNATURE).setMessage("No version of the key made the signature.");
      }
      return VerifyResponse.newBuilder().setRejected(rejected).build();
    });
  }

  @Override
  public Future<SignResponse> jwt(String type, Struct claims) {
    return primary().map(version -> {
      JWSObject draft = new JWSObject(new JWSHeader.Builder(algorithm().jose())
          .type(new JOSEObjectType(type.isEmpty() ? Jwt.TYPE : type)).keyID(version.id()).build(),
          new Payload(Claims.of(claims).json().encode()));
      String token = draft.getHeader().toBase64URL() + "." + draft.getPayload().toBase64URL() + "."
          + Base64URL.encode(((KeyVersionImpl) version).sign(draft.getSigningInput()));
      return SignResponse.newBuilder().setToken(token).setKeyVersion(version.name()).build();
    });
  }

  @Override
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
    String version = KeyTypeImpl.VERSIONS + jwt.keyId();
    return verify(jwt.signingInput(), jwt.signature(), version)
        .map(verdict -> verdict.hasRejected() ? verdict
            : jwt.claims(expected, type().schema().clock().instant(), version));
  }
}
