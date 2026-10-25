package dev.horizon.pyrite.crypto;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.CreateKeyRequest;
import dev.horizon.pyrite.crypto.v1.DecryptRequest;
import dev.horizon.pyrite.crypto.v1.DeleteKeyVersionRequest;
import dev.horizon.pyrite.crypto.v1.Detached;
import dev.horizon.pyrite.crypto.v1.EncryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.crypto.v1.GetKeyRequest;
import dev.horizon.pyrite.crypto.v1.Key;
import dev.horizon.pyrite.crypto.v1.KeyVersion;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsRequest;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.RotateKeyRequest;
import dev.horizon.pyrite.crypto.v1.SignRequest;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.crypto.v1.UpdateKeyRequest;
import dev.horizon.pyrite.crypto.v1.UpdateKeyVersionRequest;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RotationTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "organizations/acme", "");
  }

  private Key rotate(String subject, String key) throws Exception {
    return pyrite.cryptography.rotateKey(RotateKeyRequest.newBuilder().setSubject(subject).setName(key).build())
        .await(TIMEOUT);
  }

  private KeyVersion state(String version, long revision, KeyVersion.State state) throws Exception {
    return pyrite.cryptography.updateKeyVersion(UpdateKeyVersionRequest.newBuilder().setSubject(ANNE)
        .setName(version).setState(state).setRevision(revision).build()).await(TIMEOUT);
  }

  private void destroy(String version) throws Exception {
    pyrite.cryptography.deleteKeyVersion(DeleteKeyVersionRequest.newBuilder().setSubject(ANNE).setName(version)
        .build()).await(TIMEOUT);
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
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);
      EncryptResponse old = encrypt();

      Key rotated = rotate(ANNE, "keys/payments");

      assertThat(rotated.getPrimaryVersion()).isEqualTo("keyVersions/payments-2");
      assertThat(pyrite.cryptography.getKey(GetKeyRequest.newBuilder().setSubject(ANNE).setName("keys/payments")
          .build()).await(TIMEOUT).getPrimaryVersion()).isEqualTo("keyVersions/payments-2");
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-2");
      assertThat(decrypt(old.getCiphertext())).isEqualTo("card");
      assertThat(pyrite.cryptography.listKeyVersions(ListKeyVersionsRequest.newBuilder().setSubject(ANNE)
          .setKey("keys/payments").build()).await(TIMEOUT).getVersionsList())
          .extracting(KeyVersion::getName, KeyVersion::getNumber)
          .containsExactly(tuple("keyVersions/payments-1", 1L), tuple("keyVersions/payments-2", 2L));
    }

    @Test
    @DisplayName("a signing key's old version still verifies what it signed")
    void verifies() throws Exception {
      pyrite.key("organizations/acme", "codes", Algorithm.HMAC_SHA256);
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
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);
      pyrite.grant(ANNE, "keys/payments", "encrypter", BOB);

      assertThat(status(() -> rotate(BOB, "keys/payments"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("Schedule")
  class Schedule {

    @Test
    @DisplayName("a key rotates at its first use after its period, once")
    void period() throws Exception {
      pyrite.cryptography.createKey(CreateKeyRequest.newBuilder().setSubject(ANNE).setKeyId("payments")
          .setParent("organizations/acme").setAlgorithm(Algorithm.AES256_GCM)
          .setRotationPeriod(com.google.protobuf.Duration.newBuilder().setSeconds(3600)).build()).await(TIMEOUT);

      pyrite.clock.move(Duration.ofMinutes(59));
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-1");
      pyrite.clock.move(Duration.ofMinutes(2));
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-2");
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-2");
    }

    @Test
    @DisplayName("a key's rotation period changes at its revision, and runs from the change")
    void changed() throws Exception {
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);
      pyrite.clock.move(Duration.ofDays(1));

      Key changed = pyrite.cryptography.updateKey(UpdateKeyRequest.newBuilder().setSubject(ANNE)
          .setName("keys/payments").setRevision(1).setRotationPeriod(com.google.protobuf.Duration.newBuilder()
              .setSeconds(3600))
          .build()).await(TIMEOUT);

      assertThat(changed.getRevision()).isEqualTo(2);
      assertThat(changed.getNextRotationTime().getSeconds()).isEqualTo(pyrite.clock.instant().plusSeconds(3600)
          .getEpochSecond());
      assertThat(status(() -> pyrite.cryptography.updateKey(UpdateKeyRequest.newBuilder().setSubject(ANNE)
          .setName("keys/payments").setRevision(1).build()).await(TIMEOUT))).isEqualTo(GrpcStatus.ABORTED);
      pyrite.clock.move(Duration.ofMinutes(61));
      assertThat(encrypt().getKeyVersion()).isEqualTo("keyVersions/payments-2");
    }

    @Test
    @DisplayName("a key without a period never rotates on its own")
    void never() throws Exception {
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);
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
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);
      ByteString old = encrypt().getCiphertext();
      rotate(ANNE, "keys/payments");

      state("keyVersions/payments-1", 1, KeyVersion.State.DISABLED);
      assertThat(status(() -> decrypt(old))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      state("keyVersions/payments-1", 2, KeyVersion.State.ENABLED);
      assertThat(decrypt(old)).isEqualTo("card");
    }

    @Test
    @DisplayName("a destroyed version no longer decrypts")
    void deleted() throws Exception {
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);
      ByteString old = encrypt().getCiphertext();
      rotate(ANNE, "keys/payments");

      destroy("keyVersions/payments-1");

      assertThat(status(() -> decrypt(old))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("a disabled version is rejected when it verifies")
    void disabledVerify() throws Exception {
      pyrite.key("organizations/acme", "codes", Algorithm.HMAC_SHA256);
      SignResponse signed = pyrite.cryptography.sign(SignRequest.newBuilder().setSubject(ANNE).setKey("keys/codes")
          .setData(ByteString.copyFromUtf8("482913")).build()).await(TIMEOUT);
      rotate(ANNE, "keys/codes");
      state("keyVersions/codes-1", 1, KeyVersion.State.DISABLED);

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
    @DisplayName("the primary version is neither disabled nor destroyed")
    void primary() throws Exception {
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);

      assertThat(status(() -> state("keyVersions/payments-1", 1, KeyVersion.State.DISABLED)))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
      assertThat(status(() -> destroy("keyVersions/payments-1"))).isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("a version is ENABLED or DISABLED")
    void values() throws Exception {
      pyrite.key("organizations/acme", "payments", Algorithm.AES256_GCM);
      rotate(ANNE, "keys/payments");

      assertThat(status(() -> state("keyVersions/payments-1", 1, KeyVersion.State.STATE_UNSPECIFIED)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }
}
