package dev.horizon.pyrite.authentication;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.authentication.v1.Account;
import dev.horizon.pyrite.authentication.v1.AddIdentifierRequest;
import dev.horizon.pyrite.authentication.v1.CreateAccountRequest;
import dev.horizon.pyrite.authentication.v1.GetAccountRequest;
import dev.horizon.pyrite.authentication.v1.Identifier;
import dev.horizon.pyrite.authentication.v1.ListIdentifiersRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Arrays;
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

  private Identifier identifier(String subject, String account, String kind, String value) throws Exception {
    return pyrite.authentication.addIdentifier(AddIdentifierRequest.newBuilder().setSubject(subject)
        .setAccount(account).setKind(kind).setValue(value).build()).await(TIMEOUT);
  }

  private Account account(String id, String kind) throws Exception {
    Account.Kind named = Arrays.stream(Account.Kind.values()).filter(known -> known.name().equals(kind)).findFirst()
        .orElse(Account.Kind.KIND_UNSPECIFIED);
    return pyrite.authentication.createAccount(CreateAccountRequest.newBuilder().setSubject("user:" + id)
        .setAccountId(id).setKind(named).build()).await(TIMEOUT);
  }

  @Nested
  @DisplayName("Formats")
  class Formats {

    @ParameterizedTest
    @CsvSource({ "email, ' Anne.Smith@Example.COM ', anne.smith@example.com", "phone, '+420 777-123-456', +420777123456",
        "username, ' Anne_Smith ', anne_smith" })
    @DisplayName("an identifier is stored as its kind normalizes it, and unverified")
    void normalized(String kind, String given, String stored) throws Exception {
      Identifier added = identifier(ANNE, "accounts/anne", kind, given);

      assertThat(added.getValue()).isEqualTo(stored);
      assertThat(added.getKind()).isEqualTo(kind);
      assertThat(added.getVerified()).isFalse();
      assertThat(added.getAccount()).isEqualTo("accounts/anne");
    }

    @ParameterizedTest
    @CsvSource({ "email, anne", "email, anne@", "email, @acme.dev", "email, anne@acme", "email, 'an ne@acme.dev'",
        "email, anne@@acme.dev", "phone, 777123456", "phone, +0777123456", "phone, +42", "phone, +420abc",
        "username, an", "username, -anne", "username, 'an ne'", "username, anne!" })
    @DisplayName("an identifier that its kind's match refuses is refused")
    void invalid(String kind, String value) {
      assertThat(status(() -> identifier(ANNE, "accounts/anne", kind, value)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "EMAIL", "passport" })
    @DisplayName("an identifier's kind is one the declaration names")
    void kind(String kind) {
      assertThat(status(() -> identifier(ANNE, "accounts/anne", kind, "anne@acme.dev")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("an identifier that names no kind takes the first kind whose match it meets")
    void matched() throws Exception {
      Identifier email = identifier(ANNE, "accounts/anne", "", " Anne.Smith@Example.COM ");
      Identifier username = identifier(ANNE, "accounts/anne", "", "Anne_Smith");

      assertThat(email.getKind()).isEqualTo("email");
      assertThat(email.getValue()).isEqualTo("anne.smith@example.com");
      assertThat(username.getKind()).isEqualTo("username");
      assertThat(status(() -> identifier(ANNE, "accounts/anne", "", "!!"))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Ownership")
  class Ownership {

    @Test
    @DisplayName("an identifier is added only to an account that the subject administers")
    void other() throws Exception {
      account("mallory", "HUMAN");

      assertThat(status(() -> identifier("user:mallory", "accounts/anne", "email", "mallory@evil.dev")))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("an address belongs to one account")
    void unique() throws Exception {
      account("mallory", "HUMAN");

      assertThat(status(() -> identifier("user:mallory", "accounts/mallory", "email", "anne@acme.dev")))
          .isEqualTo(GrpcStatus.ALREADY_EXISTS);
      assertThat(status(() -> identifier("user:mallory", "accounts/mallory", "email", "ANNE@acme.dev")))
          .isEqualTo(GrpcStatus.ALREADY_EXISTS);
    }

    @Test
    @DisplayName("an account lists its own identifiers to itself only")
    void listed() throws Exception {
      account("mallory", "HUMAN");

      assertThat(pyrite.authentication.listIdentifiers(ListIdentifiersRequest.newBuilder().setSubject(ANNE)
          .setAccount("accounts/anne").build()).await(TIMEOUT).getIdentifiersList())
          .extracting(Identifier::getValue).containsExactly("anne@acme.dev");
      assertThat(status(() -> pyrite.authentication.listIdentifiers(ListIdentifiersRequest.newBuilder()
          .setSubject("user:mallory").setAccount("accounts/anne").build()).await(TIMEOUT)))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("Accounts")
  class Accounts {

    @ParameterizedTest
    @ValueSource(strings = { "", "PERSON", "human", "ROBOT" })
    @DisplayName("an account's kind is HUMAN or SERVICE")
    void kind(String kind) {
      assertThat(status(() -> account("bob", kind))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("an account's id is taken once")
    void taken() {
      assertThat(status(() -> account("anne", "HUMAN"))).isEqualTo(GrpcStatus.ALREADY_EXISTS);
    }

    @Test
    @DisplayName("an account owns itself and nobody else does")
    void owner() throws Exception {
      account("mallory", "HUMAN");

      assertThat(pyrite.check(ANNE, "accounts/anne", "owner", Struct.getDefaultInstance())).isTrue();
      assertThat(status(() -> pyrite.authentication.getAccount(GetAccountRequest.newBuilder()
          .setSubject("user:mallory").setName("accounts/anne").build()).await(TIMEOUT)))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }
}
