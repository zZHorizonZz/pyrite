package dev.horizon.pyrite.authentication;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.text;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.data.v1.Resource;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierTest extends PyriteTest {

  @BeforeEach
  void open() throws Exception {
    pyrite.accounts();
    pyrite.account("anne", "anne@acme.dev", "correct horse battery staple");
  }

  private Resource identifier(String subject, String account, String id, String kind, String value)
      throws Exception {
    return pyrite.create(subject, account, "pyrite.dev/Identifier", id, Map.of("kind", text(kind), "value",
        text(value)));
  }

  @Nested
  @DisplayName("Formats")
  class Formats {

    @ParameterizedTest
    @CsvSource({ "EMAIL, ' Anne.Smith@Example.COM ', anne.smith@example.com", "PHONE, '+420 777-123-456', +420777123456",
        "USERNAME, ' Anne_Smith ', anne_smith" })
    @DisplayName("an identifier is stored normalized")
    void normalized(String kind, String given, String stored) throws Exception {
      assertThat(identifier(ANNE, "accounts/anne", "other", kind, given).getFieldsMap().get("value").getStringValue())
          .isEqualTo(stored);
    }

    @ParameterizedTest
    @CsvSource({ "EMAIL, anne", "EMAIL, anne@", "EMAIL, @acme.dev", "EMAIL, anne@acme", "EMAIL, 'an ne@acme.dev'",
        "EMAIL, anne@@acme.dev", "PHONE, 777123456", "PHONE, +0777123456", "PHONE, +42", "PHONE, +420abc",
        "USERNAME, an", "USERNAME, -anne", "USERNAME, 'an ne'", "USERNAME, anne!" })
    @DisplayName("an identifier that does not match its kind's format is refused")
    void invalid(String kind, String value) {
      assertThat(status(() -> identifier(ANNE, "accounts/anne", "other", kind, value)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "email", "PASSPORT" })
    @DisplayName("an identifier's kind is EMAIL, PHONE or USERNAME")
    void kind(String kind) {
      assertThat(status(() -> identifier(ANNE, "accounts/anne", "other", kind, "anne@acme.dev")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Ownership")
  class Ownership {

    @Test
    @DisplayName("an identifier is added only to an account that the subject administers")
    void other() throws Exception {
      pyrite.create("user:mallory", "", "pyrite.dev/Account", "mallory", Map.of("kind", text("HUMAN")));

      assertThat(status(() -> identifier("user:mallory", "accounts/anne", "stolen", "EMAIL", "mallory@evil.dev")))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("an address belongs to one account")
    void unique() throws Exception {
      pyrite.create("user:mallory", "", "pyrite.dev/Account", "mallory", Map.of("kind", text("HUMAN")));

      assertThat(status(() -> identifier("user:mallory", "accounts/mallory", "work", "EMAIL", "anne@acme.dev")))
          .isEqualTo(GrpcStatus.ALREADY_EXISTS);
      assertThat(status(() -> identifier("user:mallory", "accounts/mallory", "work", "EMAIL", "ANNE@acme.dev")))
          .isEqualTo(GrpcStatus.ALREADY_EXISTS);
    }

    @Test
    @DisplayName("a verified flag is Pyrite's to set")
    void verified() {
      assertThat(status(() -> pyrite.create(ANNE, "accounts/anne", "pyrite.dev/Identifier", "other", Map.of("kind",
          text("EMAIL"), "value", text("anne.smith@acme.dev"), "verified", dev.horizon.pyrite.data.v1.Value
              .newBuilder().setBoolValue(true).build()))))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Accounts")
  class Accounts {

    @ParameterizedTest
    @ValueSource(strings = { "", "PERSON", "human", "ROBOT" })
    @DisplayName("an account's kind is HUMAN or SERVICE")
    void kind(String kind) {
      assertThat(status(() -> pyrite.create("user:bob", "", "pyrite.dev/Account", "bob", Map.of("kind",
          text(kind))))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("an account's id is taken once")
    void taken() {
      assertThat(status(() -> pyrite.create("user:anne", "", "pyrite.dev/Account", "anne", Map.of("kind",
          text("HUMAN"))))).isEqualTo(GrpcStatus.ALREADY_EXISTS);
    }

    @Test
    @DisplayName("an account owns itself and nobody else does")
    void owner() throws Exception {
      assertThat(pyrite.check(ANNE, "accounts/anne", "owner", com.google.protobuf.Struct.getDefaultInstance()))
          .isTrue();
      assertThat(status(() -> pyrite.get("user:mallory", "accounts/anne"))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }
}
