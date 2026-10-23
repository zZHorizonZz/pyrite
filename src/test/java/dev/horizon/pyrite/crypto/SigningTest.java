package dev.horizon.pyrite.crypto;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.START;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.ListValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.crypto.v1.Detached;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.SignRequest;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SigningTest extends PyriteTest {

  private static final long NOW = START.getEpochSecond();
  private static final Expected EXPECTED = Expected.newBuilder().setType("at+jwt").setIssuer("https://auth.acme.dev")
      .setAudience("https://api.acme.dev").setLeeway(Duration.newBuilder().setSeconds(30)).build();

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.key("organizations/acme", "oauth", "SIGNING", "ES256");
  }

  private static Value text(String text) {
    return Value.newBuilder().setStringValue(text).build();
  }

  private static Value number(long number) {
    return Value.newBuilder().setNumberValue(number).build();
  }

  private static Struct claims(long expires) {
    return Struct.newBuilder().putFields("iss", text("https://auth.acme.dev"))
        .putFields("aud", text("https://api.acme.dev")).putFields("sub", text("user:anne"))
        .putFields("exp", number(expires)).build();
  }

  private static String encoded(String json) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  private String sign(String key, Struct claims, String type) throws Exception {
    return pyrite.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey(key).setClaims(claims)
        .setType(type).build()).await(TIMEOUT).getToken();
  }

  private VerifyResponse verify(String subject, String key, String token, Expected expected) throws Exception {
    return pyrite.cryptography.verify(VerifyRequest.newBuilder().setSubject(subject).setKey(key).setToken(token)
        .setExpected(expected).build()).await(TIMEOUT);
  }

  private Rejected.Reason rejection(String token) throws Exception {
    return rejection(token, EXPECTED);
  }

  private Rejected.Reason rejection(String token, Expected expected) throws Exception {
    VerifyResponse verdict = verify(ANNE, "keys/oauth", token, expected);
    assertThat(verdict.hasVerified()).as("verified").isFalse();
    return verdict.getRejected().getReason();
  }

  private JWK publicKey(String version) throws Exception {
    Map<String, Object> fields = new HashMap<>();
    pyrite.get(ANNE, version).getFieldsMap().get("public_key").getMapValue().getFieldsMap()
        .forEach((name, value) -> fields.put(name, value.getStringValue()));
    return JWK.parse(fields);
  }

  @Nested
  @DisplayName("Algorithms")
  class Algorithms {

    @ParameterizedTest
    @ValueSource(strings = { "RS256", "ES256", "EDDSA" })
    @DisplayName("a signing key's JWT verifies with its published public key, and with Pyrite")
    void published(String algorithm) throws Exception {
      pyrite.key("organizations/acme", "tokens", "SIGNING", algorithm);

      String token = sign("keys/tokens", claims(NOW + 600), "at+jwt");

      assertThat(verify(ANNE, "keys/tokens", token, EXPECTED).getVerified().getClaims().getFieldsOrThrow("sub")
          .getStringValue()).isEqualTo("user:anne");
      SignedJWT jwt = SignedJWT.parse(token);
      assertThat(jwt.getHeader().getKeyID()).isEqualTo("tokens-1");
      assertThat(jwt.getHeader().getType().getType()).isEqualTo("at+jwt");
      JWK jwk = publicKey("keyVersions/tokens-1");
      assertThat(jwk.isPrivate()).isFalse();
      JWSVerifier verifier = switch (algorithm) {
        case "RS256" -> new RSASSAVerifier((RSAKey) jwk);
        case "ES256" -> new ECDSAVerifier((ECKey) jwk);
        default -> new Ed25519Verifier((OctetKeyPair) jwk);
      };
      assertThat(jwt.verify(verifier)).isTrue();
    }

    @Test
    @DisplayName("a MAC key signs a JWT and data, and publishes no key")
    void mac() throws Exception {
      pyrite.key("organizations/acme", "codes", "MAC", "HMAC_SHA256");

      assertThat(verify(ANNE, "keys/codes", sign("keys/codes", claims(NOW + 600), "at+jwt"), EXPECTED)
          .hasVerified()).isTrue();
      assertThat(pyrite.get(ANNE, "keyVersions/codes-1").getFieldsMap().get("public_key").getMapValue()
          .getFieldsCount()).isZero();
    }

    @Test
    @DisplayName("an encryption key does not sign")
    void encryption() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");

      assertThat(status(() -> sign("keys/payments", claims(NOW + 600), "at+jwt")))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }
  }

  @Nested
  @DisplayName("Claims")
  class Claims {

    @Test
    @DisplayName("a token of another issuer is rejected")
    void issuer() throws Exception {
      String token = sign("keys/oauth", claims(NOW + 600), "at+jwt");

      assertThat(rejection(token, EXPECTED.toBuilder().setIssuer("https://other.example").build()))
          .isEqualTo(Rejected.Reason.ISSUER);
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600).toBuilder().removeFields("iss").build(),
          "at+jwt"))).isEqualTo(Rejected.Reason.ISSUER);
    }

    @Test
    @DisplayName("a token for another audience is rejected, and an audience list must hold the expected one")
    void audience() throws Exception {
      Value both = Value.newBuilder().setListValue(ListValue.newBuilder().addValues(text("https://api.acme.dev"))
          .addValues(text("https://admin.acme.dev"))).build();
      Value other = Value.newBuilder().setListValue(ListValue.newBuilder().addValues(text(
          "https://admin.acme.dev"))).build();

      assertThat(rejection(sign("keys/oauth", claims(NOW + 600), "at+jwt"), EXPECTED.toBuilder()
          .setAudience("https://other.example").build())).isEqualTo(Rejected.Reason.AUDIENCE);
      assertThat(verify(ANNE, "keys/oauth", sign("keys/oauth", claims(NOW + 600).toBuilder().putFields("aud", both)
          .build(), "at+jwt"), EXPECTED).hasVerified()).isTrue();
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600).toBuilder().putFields("aud", other).build(),
          "at+jwt"))).isEqualTo(Rejected.Reason.AUDIENCE);
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600).toBuilder().removeFields("aud").build(),
          "at+jwt"))).isEqualTo(Rejected.Reason.AUDIENCE);
    }

    @Test
    @DisplayName("a token that expired, or that has no expiry, is rejected")
    void expired() throws Exception {
      assertThat(rejection(sign("keys/oauth", claims(NOW - 60), "at+jwt"))).isEqualTo(Rejected.Reason.EXPIRED);
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600).toBuilder().removeFields("exp").build(),
          "at+jwt"))).isEqualTo(Rejected.Reason.EXPIRED);
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600).toBuilder().putFields("exp", text("tomorrow"))
          .build(), "at+jwt"))).isEqualTo(Rejected.Reason.EXPIRED);
    }

    @Test
    @DisplayName("a token that is not yet valid is rejected")
    void notBefore() throws Exception {
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600).toBuilder().putFields("nbf", number(NOW + 300))
          .build(), "at+jwt"))).isEqualTo(Rejected.Reason.NOT_YET_VALID);
    }

    @Test
    @DisplayName("the leeway covers clock skew, and no more")
    void leeway() throws Exception {
      String token = sign("keys/oauth", claims(NOW - 20), "at+jwt");

      assertThat(verify(ANNE, "keys/oauth", token, EXPECTED).hasVerified()).isTrue();
      assertThat(rejection(token, EXPECTED.toBuilder().clearLeeway().build())).isEqualTo(Rejected.Reason.EXPIRED);
      assertThat(rejection(sign("keys/oauth", claims(NOW - 31), "at+jwt"))).isEqualTo(Rejected.Reason.EXPIRED);
    }

    @Test
    @DisplayName("a token of another type is rejected")
    void type() throws Exception {
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600), ""))).isEqualTo(Rejected.Reason.TYPE);
      assertThat(rejection(sign("keys/oauth", claims(NOW + 600), "id+jwt"))).isEqualTo(Rejected.Reason.TYPE);
    }
  }

  @Nested
  @DisplayName("Forgeries")
  class Forgeries {

    @Test
    @DisplayName("a token with a changed payload fails its signature")
    void payload() throws Exception {
      String[] parts = sign("keys/oauth", claims(NOW + 600), "at+jwt").split("\\.");
      String forged = encoded("{\"iss\":\"https://auth.acme.dev\",\"aud\":\"https://api.acme.dev\","
          + "\"sub\":\"user:root\",\"exp\":" + (NOW + 600) + "}");

      assertThat(rejection(parts[0] + "." + forged + "." + parts[2])).isEqualTo(Rejected.Reason.SIGNATURE);
    }

    @Test
    @DisplayName("a token with a changed or missing signature is rejected")
    void signature() throws Exception {
      String[] parts = sign("keys/oauth", claims(NOW + 600), "at+jwt").split("\\.");

      assertThat(rejection(parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse()))
          .isIn(Rejected.Reason.SIGNATURE, Rejected.Reason.MALFORMED);
      assertThat(rejection(parts[0] + "." + parts[1] + ".")).isIn(Rejected.Reason.SIGNATURE,
          Rejected.Reason.MALFORMED);
      assertThat(rejection(parts[0] + "." + parts[1])).isEqualTo(Rejected.Reason.MALFORMED);
    }

    @Test
    @DisplayName("a token that claims no algorithm is rejected")
    void none() throws Exception {
      String[] parts = sign("keys/oauth", claims(NOW + 600), "at+jwt").split("\\.");
      String header = encoded("{\"alg\":\"none\",\"typ\":\"at+jwt\",\"kid\":\"oauth-1\"}");

      assertThat(rejection(header + "." + parts[1] + ".")).isIn(Rejected.Reason.MALFORMED,
          Rejected.Reason.ALGORITHM);
    }

    @Test
    @DisplayName("a token signed with HS256 over the published public key is rejected")
    void confusion() throws Exception {
      byte[] secret = MessageDigest.getInstance("SHA-256").digest(publicKey("keyVersions/oauth-1").toJSONString()
          .getBytes(StandardCharsets.UTF_8));
      SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("at+jwt"))
          .keyID("oauth-1").build(),
          new JWTClaimsSet.Builder().issuer("https://auth.acme.dev")
              .audience("https://api.acme.dev").subject("user:root").expirationTime(new Date((NOW + 600) * 1000))
              .build());
      forged.sign(new MACSigner(secret));

      assertThat(rejection(forged.serialize())).isEqualTo(Rejected.Reason.ALGORITHM);
    }

    @Test
    @DisplayName("a token of another key with its key id changed to this key's fails its signature")
    void keyId() throws Exception {
      pyrite.key("organizations/acme", "other", "SIGNING", "ES256");
      String[] parts = sign("keys/other", claims(NOW + 600), "at+jwt").split("\\.");
      String header = encoded("{\"kid\":\"oauth-1\",\"typ\":\"at+jwt\",\"alg\":\"ES256\"}");

      assertThat(rejection(header + "." + parts[1] + "." + parts[2])).isEqualTo(Rejected.Reason.SIGNATURE);
    }

    @Test
    @DisplayName("a token of another key, or with no key id, names no version of this key")
    void otherKey() throws Exception {
      pyrite.key("organizations/acme", "other", "SIGNING", "ES256");
      String[] parts = sign("keys/oauth", claims(NOW + 600), "at+jwt").split("\\.");

      assertThat(rejection(sign("keys/other", claims(NOW + 600), "at+jwt"))).isEqualTo(
          Rejected.Reason.UNKNOWN_VERSION);
      assertThat(rejection(encoded("{\"typ\":\"at+jwt\",\"alg\":\"ES256\"}") + "." + parts[1] + "." + parts[2]))
          .isEqualTo(Rejected.Reason.UNKNOWN_VERSION);
      assertThat(rejection(encoded("{\"kid\":\"oauth-9\",\"typ\":\"at+jwt\",\"alg\":\"ES256\"}") + "." + parts[1]
          + "." + parts[2])).isEqualTo(Rejected.Reason.UNKNOWN_VERSION);
    }

    @Test
    @DisplayName("text that is not a token is rejected")
    void malformed() throws Exception {
      for (String token : new String[] { "", "not a token", "a.b.c", "....", encoded("{}") + "." + encoded("{}")
          + "." }) {
        assertThat(rejection(token)).as(token).isEqualTo(Rejected.Reason.MALFORMED);
      }
    }

    @Test
    @DisplayName("a token that marks a header parameter critical is rejected")
    void critical() throws Exception {
      String[] parts = sign("keys/oauth", claims(NOW + 600), "at+jwt").split("\\.");
      String header = encoded("{\"kid\":\"oauth-1\",\"typ\":\"at+jwt\",\"alg\":\"ES256\",\"crit\":[\"exp\"],"
          + "\"exp\":1}");

      assertThat(rejection(header + "." + parts[1] + "." + parts[2])).isIn(Rejected.Reason.MALFORMED,
          Rejected.Reason.SIGNATURE);
    }
  }

  @Nested
  @DisplayName("Detached signatures")
  class DetachedSignatures {

    @BeforeEach
    void key() throws Exception {
      pyrite.key("organizations/acme", "codes", "MAC", "HMAC_SHA256");
    }

    private ByteString signed(String key, String data) throws Exception {
      return pyrite.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey(key).setData(ByteString
          .copyFromUtf8(data)).build()).await(TIMEOUT).getSignature();
    }

    private VerifyResponse verified(String data, ByteString signature, String version) throws Exception {
      return pyrite.cryptography.verify(VerifyRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
          .setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8(data)).setSignature(signature)
              .setKeyVersion(version))
          .build()).await(TIMEOUT);
    }

    @Test
    @DisplayName("a signature verifies only its own data")
    void data() throws Exception {
      ByteString signature = signed("keys/codes", "482913");

      assertThat(verified("482913", signature, "").hasVerified()).isTrue();
      assertThat(verified("482914", signature, "").getRejected().getReason()).isEqualTo(Rejected.Reason.SIGNATURE);
      assertThat(verified("", signature, "").getRejected().getReason()).isEqualTo(Rejected.Reason.SIGNATURE);
    }

    @Test
    @DisplayName("a signature of another key, an empty one, or a changed one is rejected")
    void forged() throws Exception {
      pyrite.key("organizations/acme", "other", "MAC", "HMAC_SHA256");
      ByteString signature = signed("keys/codes", "482913");
      byte[] changed = signature.toByteArray();
      changed[0] ^= 1;

      assertThat(verified("482913", signed("keys/other", "482913"), "").getRejected().getReason())
          .isEqualTo(Rejected.Reason.SIGNATURE);
      assertThat(verified("482913", ByteString.EMPTY, "").getRejected().getReason())
          .isEqualTo(Rejected.Reason.SIGNATURE);
      assertThat(verified("482913", ByteString.copyFrom(changed), "").getRejected().getReason())
          .isEqualTo(Rejected.Reason.SIGNATURE);
    }

    @Test
    @DisplayName("a named version that the key does not have is rejected")
    void version() throws Exception {
      pyrite.key("organizations/acme", "other", "MAC", "HMAC_SHA256");
      ByteString signature = signed("keys/codes", "482913");

      assertThat(verified("482913", signature, "keyVersions/other-1").getRejected().getReason())
          .isEqualTo(Rejected.Reason.UNKNOWN_VERSION);
      assertThat(verified("482913", signature, "keyVersions/codes-7").getRejected().getReason())
          .isEqualTo(Rejected.Reason.UNKNOWN_VERSION);
    }
  }

  @Nested
  @DisplayName("Permissions and requests")
  class PermissionsAndRequests {

    @Test
    @DisplayName("signing and verifying need their relations")
    void relations() throws Exception {
      String token = sign("keys/oauth", claims(NOW + 600), "at+jwt");

      assertThat(status(() -> verify(BOB, "keys/oauth", token, EXPECTED))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      pyrite.grant(ANNE, "keys/oauth", "verifier", "user:*");
      assertThat(verify(BOB, "keys/oauth", token, EXPECTED).hasVerified()).isTrue();
      assertThat(status(() -> pyrite.cryptography.sign(SignRequest.newBuilder().setSubject(BOB).setKey("keys/oauth")
          .setClaims(claims(NOW + 600)).build()).await(TIMEOUT))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a request names what to sign and what to verify")
    void payload() {
      assertThat(status(() -> pyrite.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey("keys/oauth")
          .build()).await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.cryptography.verify(VerifyRequest.newBuilder().setSubject(ANNE)
          .setKey("keys/oauth").build()).await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
