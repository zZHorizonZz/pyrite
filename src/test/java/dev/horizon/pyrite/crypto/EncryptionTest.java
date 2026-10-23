package dev.horizon.pyrite.crypto;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.CAROL;
import static dev.horizon.pyrite.Pyrite.integer;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.text;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.ByteString;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.crypto.v1.DecryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EncryptionTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    pyrite.key("organizations/acme", "payments", "ENCRYPTION", "AES256_GCM");
  }

  private EncryptResponse encrypt(String subject, String key, byte[] plaintext, String associatedData)
      throws Exception {
    return pyrite.cryptography.encrypt(EncryptRequest.newBuilder().setSubject(subject).setKey(key)
        .setPlaintext(ByteString.copyFrom(plaintext)).setAssociatedData(ByteString.copyFromUtf8(associatedData))
        .build()).await(TIMEOUT);
  }

  private ByteString sealed(String plaintext, String associatedData) throws Exception {
    return encrypt(ANNE, "keys/payments", plaintext.getBytes(StandardCharsets.UTF_8), associatedData)
        .getCiphertext();
  }

  private ByteString decrypt(String subject, String key, ByteString ciphertext, String associatedData)
      throws Exception {
    return pyrite.cryptography.decrypt(DecryptRequest.newBuilder().setSubject(subject).setKey(key)
        .setCiphertext(ciphertext).setAssociatedData(ByteString.copyFromUtf8(associatedData)).build())
        .await(TIMEOUT).getPlaintext();
  }

  @Nested
  @DisplayName("Round trip")
  class RoundTrip {

    @Test
    @DisplayName("a key decrypts what it encrypted, and names the version")
    void decrypts() throws Exception {
      EncryptResponse sealed = encrypt(ANNE, "keys/payments", "4111 1111 1111 1111".getBytes(
          StandardCharsets.UTF_8), "orders/1042");

      assertThat(sealed.getKeyVersion()).isEqualTo("keyVersions/payments-1");
      assertThat(sealed.getCiphertext().toStringUtf8()).doesNotContain("4111");
      assertThat(decrypt(ANNE, "keys/payments", sealed.getCiphertext(), "orders/1042").toStringUtf8())
          .isEqualTo("4111 1111 1111 1111");
    }

    @Test
    @DisplayName("empty, binary and large plaintexts survive the round trip")
    void sizes() throws Exception {
      byte[] binary = new byte[256];
      for (int index = 0; index < binary.length; index++) {
        binary[index] = (byte) index;
      }
      for (byte[] plaintext : new byte[][] { new byte[0], binary, new byte[1 << 20] }) {
        ByteString sealed = encrypt(ANNE, "keys/payments", plaintext, "").getCiphertext();
        assertThat(decrypt(ANNE, "keys/payments", sealed, "").toByteArray()).isEqualTo(plaintext);
      }
    }

    @Test
    @DisplayName("every encryption of the same plaintext is different")
    void randomized() throws Exception {
      Set<ByteString> seen = new HashSet<>();
      for (int index = 0; index < 50; index++) {
        seen.add(sealed("card", "a"));
      }

      assertThat(seen).hasSize(50);
    }
  }

  @Nested
  @DisplayName("Tampering")
  class Tampering {

    @Test
    @DisplayName("a ciphertext with any byte changed is refused")
    void flipped() throws Exception {
      byte[] sealed = sealed("card", "orders/1042").toByteArray();

      for (int index = 0; index < sealed.length; index++) {
        byte[] changed = sealed.clone();
        changed[index] ^= 1;
        int position = index;
        assertThat(status(() -> decrypt(ANNE, "keys/payments", ByteString.copyFrom(changed), "orders/1042")))
            .as("byte %d", position).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      }
    }

    @Test
    @DisplayName("a ciphertext cut short anywhere is refused")
    void truncated() throws Exception {
      ByteString sealed = sealed("card", "orders/1042");

      for (int length = 0; length < sealed.size(); length++) {
        ByteString cut = sealed.substring(0, length);
        assertThat(status(() -> decrypt(ANNE, "keys/payments", cut, "orders/1042"))).as("%d bytes", length)
            .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      }
    }

    @Test
    @DisplayName("a ciphertext with bytes added is refused")
    void extended() throws Exception {
      ByteString sealed = sealed("card", "orders/1042");

      assertThat(status(() -> decrypt(ANNE, "keys/payments", sealed.concat(ByteString.copyFromUtf8("x")),
          "orders/1042"))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a ciphertext that names another version of its key is refused")
    void otherVersion() throws Exception {
      ByteString sealed = sealed("card", "orders/1042");
      pyrite.data.createResource(CreateResourceRequest.newBuilder().setSubject(ANNE).setParent("keys/payments")
          .setResource(Resource.newBuilder().setType("pyrite.dev/KeyVersion")).build()).await(TIMEOUT);
      byte[] renamed = sealed.toByteArray();
      String header = new String(renamed, 1, renamed[0], StandardCharsets.US_ASCII);
      byte[] other = header.replace("payments-1", "payments-2").getBytes(StandardCharsets.US_ASCII);
      System.arraycopy(other, 0, renamed, 1, other.length);

      assertThat(status(() -> decrypt(ANNE, "keys/payments", ByteString.copyFrom(renamed), "orders/1042")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @CsvSource({ "orders/1042, orders/1043", "orders/1042, ''", "'', orders/1042", "orders/1042, ORDERS/1042" })
    @DisplayName("a ciphertext decrypts only with its own associated data")
    void associatedData(String encrypted, String decrypted) throws Exception {
      ByteString sealed = sealed("card", encrypted);

      assertThat(status(() -> decrypt(ANNE, "keys/payments", sealed, decrypted)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a ciphertext decrypts only with the key that made it")
    void otherKey() throws Exception {
      pyrite.key("organizations/acme", "billing", "ENCRYPTION", "AES256_GCM");
      ByteString sealed = sealed("card", "a");

      assertThat(status(() -> decrypt(ANNE, "keys/billing", sealed, "a"))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("bytes that no key made are refused")
    void garbage() {
      for (String garbage : new String[] { "", "x", "\u0005payments-1", "ÿpayments-1abc" }) {
        assertThat(status(() -> decrypt(ANNE, "keys/payments", ByteString.copyFromUtf8(garbage), "a")))
            .as(garbage).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      }
    }
  }

  @Nested
  @DisplayName("Permissions")
  class Permissions {

    @Test
    @DisplayName("a principal without a relation on the key neither encrypts nor decrypts")
    void none() throws Exception {
      ByteString sealed = sealed("card", "a");

      assertThat(status(() -> encrypt(BOB, "keys/payments", new byte[] { 1 }, "a")))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> decrypt(BOB, "keys/payments", sealed, "a"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a decrypter only decrypts, and an encrypter only encrypts")
    void separate() throws Exception {
      ByteString sealed = sealed("card", "a");
      pyrite.grant(ANNE, "keys/payments", "decrypter", BOB);
      pyrite.grant(ANNE, "keys/payments", "encrypter", CAROL);

      assertThat(decrypt(BOB, "keys/payments", sealed, "a").toStringUtf8()).isEqualTo("card");
      assertThat(status(() -> encrypt(BOB, "keys/payments", new byte[] { 1 }, "a")))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(encrypt(CAROL, "keys/payments", new byte[] { 1 }, "a").getKeyVersion()).isNotEmpty();
      assertThat(status(() -> decrypt(CAROL, "keys/payments", sealed, "a"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a viewer of the key does not decrypt")
    void viewer() throws Exception {
      ByteString sealed = sealed("card", "a");
      pyrite.grant(ANNE, "keys/payments", "viewer", BOB);

      assertThat(status(() -> decrypt(BOB, "keys/payments", sealed, "a"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a revoked decrypter no longer decrypts")
    void revoked() throws Exception {
      ByteString sealed = sealed("card", "a");
      pyrite.grant(ANNE, "keys/payments", "decrypter", BOB);
      pyrite.access.deleteGrant(DeleteGrantRequest.newBuilder().setSubject(ANNE).setGrant(Grant.newBuilder()
          .setObject("keys/payments").setRelation("decrypter").setGrantee(BOB)).build()).await(TIMEOUT);

      assertThat(status(() -> decrypt(BOB, "keys/payments", sealed, "a"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("Keys")
  class Keys {

    @Test
    @DisplayName("only an encryption key encrypts")
    void purpose() throws Exception {
      pyrite.key("organizations/acme", "codes", "MAC", "HMAC_SHA256");
      pyrite.key("organizations/acme", "oauth", "SIGNING", "ES256");

      assertThat(status(() -> encrypt(ANNE, "keys/codes", new byte[] { 1 }, ""))).isEqualTo(
          GrpcStatus.FAILED_PRECONDITION);
      assertThat(status(() -> encrypt(ANNE, "keys/oauth", new byte[] { 1 }, ""))).isEqualTo(
          GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("a missing or deleted key does not encrypt")
    void missing() throws Exception {
      assertThat(status(() -> encrypt(ANNE, "keys/missing", new byte[] { 1 }, ""))).isEqualTo(
          GrpcStatus.NOT_FOUND);
      pyrite.data.deleteResource(DeleteResourceRequest.newBuilder().setSubject(ANNE).setName("keys/payments")
          .setRevision(1).build()).await(TIMEOUT);
      assertThat(status(() -> encrypt(ANNE, "keys/payments", new byte[] { 1 }, ""))).isEqualTo(
          GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("a name that is not a key's is refused")
    void notKey() {
      assertThat(status(() -> encrypt(ANNE, "organizations/acme", new byte[] { 1 }, "")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @CsvSource({ "SIGNING, AES256_GCM", "ENCRYPTION, HMAC_SHA256", "ENCRYPTION, ES256", "MAC, AES256_GCM",
        "ENCRYPTION, DES", "STORAGE, AES256_GCM", "ENCRYPTION, ''", "'', AES256_GCM", "encryption, aes256_gcm" })
    @DisplayName("a key names an algorithm of its purpose")
    void algorithm(String purpose, String algorithm) {
      assertThat(status(() -> pyrite.key("organizations/acme", "invalid", purpose, algorithm)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a rotation period is not negative")
    void period() {
      assertThat(status(() -> pyrite.create(ANNE, "organizations/acme", "pyrite.dev/Key", "invalid", Map.of(
          "purpose", text("ENCRYPTION"), "algorithm", text("AES256_GCM"), "rotation_period", integer(-1)))))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("creating a key needs the create permission on its parent")
    void create() {
      assertThat(status(() -> pyrite.create(BOB, "organizations/acme", "pyrite.dev/Key", "mine", Map.of(
          "purpose", text("ENCRYPTION"), "algorithm", text("AES256_GCM"))))).isEqualTo(
              GrpcStatus.PERMISSION_DENIED);
    }
  }
}
