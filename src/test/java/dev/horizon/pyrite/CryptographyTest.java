package dev.horizon.pyrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Struct;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jwt.SignedJWT;
import dev.horizon.pyrite.crypto.v1.DecryptRequest;
import dev.horizon.pyrite.crypto.v1.Detached;
import dev.horizon.pyrite.crypto.v1.EncryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.SignRequest;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.GetResourceRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.UpdateResourceRequest;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.impl.model.TypeSystem;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptographyTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final String ANNE = "user:anne";
  private static final String BOB = "user:bob";

  private Installation installation;

  @BeforeEach
  void start() throws Exception {
    installation = Installation.start(START);
    create("", "acme.dev/Organization", "acme", Map.of());
  }

  @AfterEach
  void stop() throws Exception {
    installation.close();
  }

  @Test
  void anEncryptionKeyDecryptsWhatItEncrypted() throws Exception {
    Resource key = key("payments", "ENCRYPTION", "AES256_GCM", 0);
    assertThat(key.getFieldsMap().get("primary_version").getStringValue()).isEqualTo("keyVersions/payments-1");

    EncryptResponse sealed = encrypt(ANNE, "card", "order-1");

    assertThat(sealed.getKeyVersion()).isEqualTo("keyVersions/payments-1");
    assertThat(decrypt(ANNE, sealed.getCiphertext(), "order-1")).isEqualTo("card");
    assertThat(status(() -> decrypt(ANNE, sealed.getCiphertext(), "order-2"))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    assertThat(status(() -> encrypt(BOB, "card", "order-1"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    Resource version = installation.data.getResource(GetResourceRequest.newBuilder().setSubject(ANNE)
        .setName("keyVersions/payments-1").build()).await(10, TimeUnit.SECONDS);
    assertThat(version.getFieldsMap()).doesNotContainKeys("sealed", "public_key_info")
        .containsKey("number");
  }

  @Test
  void aRotationKeepsWhatOlderVersionsEncryptedReadable() throws Exception {
    key("payments", "ENCRYPTION", "AES256_GCM", 0);
    EncryptResponse old = encrypt(ANNE, "card", "order-1");

    Resource rotated = installation.data.createResource(CreateResourceRequest.newBuilder().setSubject(ANNE)
        .setParent("keys/payments").setResource(Resource.newBuilder().setType(TypeSystem.KEY_VERSION)).build())
        .await(10, TimeUnit.SECONDS);

    assertThat(rotated.getName()).isEqualTo("keyVersions/payments-2");
    assertThat(encrypt(ANNE, "card", "order-2").getKeyVersion()).isEqualTo("keyVersions/payments-2");
    assertThat(decrypt(ANNE, old.getCiphertext(), "order-1")).isEqualTo("card");
    assertThat(status(() -> state("keyVersions/payments-2", 1, "DISABLED")))
        .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    assertThat(status(() -> installation.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(ANNE)
        .setName("keyVersions/payments-2").setRevision(1).build()).await(10, TimeUnit.SECONDS)))
        .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    state("keyVersions/payments-1", 1, "DISABLED");
    assertThat(status(() -> decrypt(ANNE, old.getCiphertext(), "order-1")))
        .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    assertThat(installation.data.queryResources(QueryResourcesRequest.newBuilder().setSubject(ANNE)
        .setType(TypeSystem.KEY_VERSION).setOrderBy("number").build()).await(10, TimeUnit.SECONDS).getResourcesList())
        .extracting(Resource::getName).containsExactly("keyVersions/payments-1", "keyVersions/payments-2");
  }

  @Test
  void aKeyRotatesWhenItsPeriodEnds() throws Exception {
    key("payments", "ENCRYPTION", "AES256_GCM", 3600);
    installation.clock.move(java.time.Duration.ofHours(2));

    assertThat(encrypt(ANNE, "card", "order-1").getKeyVersion()).isEqualTo("keyVersions/payments-2");
  }

  @Test
  void aSigningKeySignsJwtsThatItsPublicKeyVerifies() throws Exception {
    key("oauth", "SIGNING", "ES256", 0);
    long now = START.getEpochSecond();
    Struct claims = Struct.newBuilder()
        .putFields("iss", text("https://auth.acme.dev")).putFields("aud", text("https://api.acme.dev"))
        .putFields("sub", text("user:anne")).putFields("exp", number(now + 600)).build();

    SignResponse signed = installation.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey("keys/oauth")
        .setClaims(claims).setType("at+jwt").build()).await(10, TimeUnit.SECONDS);
    Expected expected = Expected.newBuilder().setType("at+jwt").setIssuer("https://auth.acme.dev")
        .setAudience("https://api.acme.dev").setLeeway(Duration.newBuilder().setSeconds(30)).build();

    VerifyResponse verified = verify(signed.getToken(), expected);
    assertThat(verified.getVerified().getClaims().getFieldsOrThrow("sub").getStringValue()).isEqualTo("user:anne");
    assertThat(verify(signed.getToken(), expected.toBuilder().setAudience("https://other.example").build())
        .getRejected().getReason()).isEqualTo(Rejected.Reason.AUDIENCE);
    Resource version = installation.data.getResource(GetResourceRequest.newBuilder().setSubject(ANNE)
        .setName(signed.getKeyVersion()).build()).await(10, TimeUnit.SECONDS);
    Map<String, Object> jwk = new HashMap<>();
    version.getFieldsMap().get("public_key").getMapValue().getFieldsMap()
        .forEach((name, value) -> jwk.put(name, value.getStringValue()));
    ECKey published = ECKey.parse(jwk);
    assertThat(SignedJWT.parse(signed.getToken()).verify(new ECDSAVerifier(published))).isTrue();

    installation.clock.move(java.time.Duration.ofHours(1));
    assertThat(verify(signed.getToken(), expected).getRejected().getReason()).isEqualTo(Rejected.Reason.EXPIRED);
  }

  @Test
  void aMacKeyVerifiesOnlyWhatItSigned() throws Exception {
    key("codes", "MAC", "HMAC_SHA256", 0);
    SignResponse signed = installation.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
        .setData(ByteString.copyFromUtf8("482913")).build()).await(10, TimeUnit.SECONDS);

    assertThat(detached("482913", signed.getSignature()).hasVerified()).isTrue();
    assertThat(detached("482914", signed.getSignature()).getRejected().getReason())
        .isEqualTo(Rejected.Reason.SIGNATURE);
  }

  @Test
  void aKeyIsDeclaredWithAnAlgorithmOfItsPurpose() {
    assertThat(status(() -> key("payments", "SIGNING", "AES256_GCM", 0))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    assertThat(status(() -> key("payments", "ENCRYPTION", "DES", 0))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
  }

  private Resource key(String id, String purpose, String algorithm, long period) throws Exception {
    return create("organizations/acme", TypeSystem.KEY, id, Map.of("purpose", value(purpose), "algorithm",
        value(algorithm), "rotation_period", Value.newBuilder().setIntegerValue(period).build()));
  }

  private Resource create(String parent, String type, String id, Map<String, Value> fields) throws Exception {
    return installation.data.createResource(CreateResourceRequest.newBuilder().setSubject(ANNE).setParent(parent)
        .setResourceId(id).setResource(Resource.newBuilder().setType(type).putAllFields(fields)).build())
        .await(10, TimeUnit.SECONDS);
  }

  private Resource state(String version, long revision, String state) throws Exception {
    return installation.data.updateResource(UpdateResourceRequest.newBuilder().setSubject(ANNE)
        .setResource(Resource.newBuilder().setName(version).putFields("state", value(state)))
        .setUpdateMask(FieldMask.newBuilder().addPaths("state")).setRevision(revision).build())
        .await(10, TimeUnit.SECONDS);
  }

  private EncryptResponse encrypt(String subject, String plaintext, String associatedData) throws Exception {
    return installation.cryptography.encrypt(EncryptRequest.newBuilder().setSubject(subject).setKey("keys/payments")
        .setPlaintext(ByteString.copyFromUtf8(plaintext)).setAssociatedData(ByteString.copyFromUtf8(associatedData))
        .build()).await(10, TimeUnit.SECONDS);
  }

  private String decrypt(String subject, ByteString ciphertext, String associatedData) throws Exception {
    return installation.cryptography.decrypt(DecryptRequest.newBuilder().setSubject(subject).setKey("keys/payments")
        .setCiphertext(ciphertext).setAssociatedData(ByteString.copyFromUtf8(associatedData)).build())
        .await(10, TimeUnit.SECONDS).getPlaintext().toString(StandardCharsets.UTF_8);
  }

  private VerifyResponse verify(String token, Expected expected) throws Exception {
    return installation.cryptography.verify(VerifyRequest.newBuilder().setSubject(ANNE).setKey("keys/oauth")
        .setToken(token).setExpected(expected).build()).await(10, TimeUnit.SECONDS);
  }

  private VerifyResponse detached(String data, ByteString signature) throws Exception {
    return installation.cryptography.verify(VerifyRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
        .setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8(data)).setSignature(signature)).build())
        .await(10, TimeUnit.SECONDS);
  }

  private static Value value(String text) {
    return Value.newBuilder().setStringValue(text).build();
  }

  private static com.google.protobuf.Value text(String text) {
    return com.google.protobuf.Value.newBuilder().setStringValue(text).build();
  }

  private static com.google.protobuf.Value number(long number) {
    return com.google.protobuf.Value.newBuilder().setNumberValue(number).build();
  }

  private static GrpcStatus status(ThrowingCallable call) {
    GrpcStatus[] status = new GrpcStatus[1];
    assertThatThrownBy(call).isInstanceOfSatisfying(InvalidStatusException.class,
        failure -> status[0] = failure.actualStatus());
    return status[0];
  }
}
