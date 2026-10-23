package dev.horizon.pyrite.crypto;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.integer;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.text;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import com.google.protobuf.FieldMask;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.crypto.v1.DecryptRequest;
import dev.horizon.pyrite.crypto.v1.Detached;
import dev.horizon.pyrite.crypto.v1.EncryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.SignRequest;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;
import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.UpdateResourceRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RotationTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
  }

  private Resource rotate(String subject, String key) throws Exception {
    return pyrite.data.createResource(CreateResourceRequest.newBuilder().setSubject(subject).setParent(key)
        .setResource(Resource.newBuilder().setType("pyrite.dev/KeyVersion")).build()).await(TIMEOUT);
  }

  private Resource state(String version, long revision, String state) throws Exception {
    return pyrite.data.updateResource(UpdateResourceRequest.newBuilder().setSubject(ANNE)
        .setResource(Resource.newBuilder().setName(version).putFields("state", text(state)))
        .setUpdateMask(FieldMask.newBuilder().addPaths("state")).setRevision(revision).build()).await(TIMEOUT);
  }

  private EncryptResponse encrypt() throws Exception {
    return pyrite.cryptography.encrypt(EncryptRequest.newBuilder().setSubject(ANNE).setKey("keys/payments")
        .setPlaintext(ByteString.copyFromUtf8("card")).build()).await(TIMEOUT);
  }

  private String decrypt(ByteString ciphertext) throws Exception {
    return pyrite.cryptography.decrypt(DecryptRequest.newBuilder().setSubject(ANNE).setKey("keys/payments")
        .setCiphertext(ciphertext).build()).await(TIMEOUT).getPlaintext().toStringUtf8();
  }

  @Nested
  @DisplayName("Rotation")
  class Rotation {

    @Test
    @DisplayName("a rotation makes the next version primary and keeps the old one readable")
    void rotates() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
      EncryptResponse old = encrypt();

      Resource second = rotate(ANNE, "keys/payments");

      assertThat(second.getName()).isEqualTo("keyVersions/payments-2");
      assertThat(second.getFieldsMap().get("number").getIntegerValue()).isEqualTo(2);
      assertThat(pyrite.get(ANNE, "keys/payments").getFieldsMap().get("primary_version").getStringValue())
          .isEqualTo("keyVersions/payments-2");
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-2");
      assertThat(decrypt(old.getCiphertext())).isEqualTo("card");
      assertThat(pyrite.query(QueryResourcesRequest.newBuilder().setSubject(ANNE).setType("pyrite.dev/KeyVersion")
          .setOrderBy("number").build())).containsExactly("keyVersions/payments-1", "keyVersions/payments-2");
    }

    @Test
    @DisplayName("a signing key's old version still verifies what it signed")
    void verifies() throws Exception {
      pyrite.key("organizations/acme", "codes", "MAC", "HMAC_SHA256");
      SignResponse signed = pyrite.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
          .setData(ByteString.copyFromUtf8("482913")).build()).await(TIMEOUT);

      rotate(ANNE, "keys/codes");

      assertThat(pyrite.cryptography.verify(VerifyRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
          .setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8("482913"))
              .setSignature(signed.getSignature()))
          .build()).await(TIMEOUT).getVerified().getKeyVersion())
          .isEqualTo("keyVersions/codes-1");
    }

    @Test
    @DisplayName("a rotation needs the admin relation on the key")
    void permission() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
      pyrite.grant(ANNE, "keys/payments", "encrypter", BOB);

      assertThat(status(() -> rotate(BOB, "keys/payments"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("Pyrite names and fills a version")
    void named() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");

      assertThat(status(() -> pyrite.data.createResource(CreateResourceRequest.newBuilder().setSubject(ANNE)
          .setParent("keys/payments").setResourceId("mine").setResource(Resource.newBuilder()
              .setType("pyrite.dev/KeyVersion"))
          .build()).await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.data.createResource(CreateResourceRequest.newBuilder().setSubject(ANNE)
          .setParent("keys/payments").setResource(Resource.newBuilder().setType("pyrite.dev/KeyVersion")
              .putFields("number", integer(9)))
          .build()).await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Schedule")
  class Schedule {

    @Test
    @DisplayName("a key rotates at its first use after its period, once")
    void period() throws Exception {
      pyrite.create(ANNE, "organizations/acme", "pyrite.dev/Key", "payments", Map.of("purpose", text("ENCRYPTION"),
          "algorithm", text("AES256_GCM"), "rotation_period", integer(3600)));

      pyrite.clock.move(Duration.ofMinutes(59));
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-1");
      pyrite.clock.move(Duration.ofMinutes(2));
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-2");
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-2");
    }

    @Test
    @DisplayName("a key without a period never rotates on its own")
    void never() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
      pyrite.clock.move(Duration.ofDays(3650));

      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-1");
    }
  }

  @Nested
  @DisplayName("States")
  class States {

    @Test
    @DisplayName("a disabled version no longer decrypts until it is enabled again")
    void disabled() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
      ByteString old = encrypt().getCiphertext();
      rotate(ANNE, "keys/payments");

      state("keyVersions/payments-1", 1, "DISABLED");
      assertThat(status(() -> decrypt(old))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      state("keyVersions/payments-1", 2, "ENABLED");
      assertThat(decrypt(old)).isEqualTo("card");
    }

    @Test
    @DisplayName("a deleted version no longer decrypts")
    void deleted() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
      ByteString old = encrypt().getCiphertext();
      rotate(ANNE, "keys/payments");

      pyrite.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(ANNE)
          .setName("keyVersions/payments-1").setRevision(1).build()).await(TIMEOUT);

      assertThat(status(() -> decrypt(old))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("a disabled version is rejected when it verifies")
    void disabledVerify() throws Exception {
      pyrite.key("organizations/acme", "codes", "MAC", "HMAC_SHA256");
      SignResponse signed = pyrite.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
          .setData(ByteString.copyFromUtf8("482913")).build()).await(TIMEOUT);
      rotate(ANNE, "keys/codes");
      state("keyVersions/codes-1", 1, "DISABLED");

      assertThat(pyrite.cryptography.verify(VerifyRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
          .setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8("482913"))
              .setSignature(signed.getSignature()).setKeyVersion("keyVersions/codes-1"))
          .build()).await(TIMEOUT).getRejected().getReason()).isEqualTo(Rejected.Reason.DISABLED_VERSION);
      assertThat(pyrite.cryptography.verify(VerifyRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
          .setDetached(Detached.newBuilder().setData(ByteString.copyFromUtf8("482913"))
              .setSignature(signed.getSignature()))
          .build()).await(TIMEOUT).hasVerified()).isFalse();
    }

    @Test
    @DisplayName("the primary version is neither disabled nor deleted")
    void primary() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");

      assertThat(status(() -> state("keyVersions/payments-1", 1, "DISABLED")))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      assertThat(status(() -> pyrite.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(ANNE)
          .setName("keyVersions/payments-1").setRevision(1).build()).await(TIMEOUT)))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("a version is ENABLED or DISABLED, nothing else")
    void values() throws Exception {
      pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
      rotate(ANNE, "keys/payments");

      for (String state : new String[] { "LOST", "disabled", "", "DESTROYED" }) {
        assertThat(status(() -> state("keyVersions/payments-1", 1, state))).as(state)
            .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      }
    }
  }
}
