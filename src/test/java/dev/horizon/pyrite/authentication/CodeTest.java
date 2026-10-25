package dev.horizon.pyrite.authentication;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.authentication.v1.AddIdentifierRequest;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.CodeSent;
import dev.horizon.pyrite.authentication.v1.Delivery;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CodeTest extends PyriteTest {

  private static final String PASSWORD = "correct horse battery staple";

  @BeforeEach
  void open() throws Exception {
    pyrite.accounts();
    pyrite.account("anne", "anne@acme.dev", PASSWORD);
  }

  private static CodeAnswer code(String code) {
    return CodeAnswer.newBuilder().setCode(code).build();
  }

  private static String wrong(String code) {
    return String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);
  }

  @Nested
  @DisplayName("Verification")
  class Verification {

    @Test
    @DisplayName("the code sent to an address verifies it")
    void verifies() throws Exception {
      Flow flow = pyrite.start(ANNE, "verify", "anne@acme.dev");
      Delivery delivery = flow.getDeliveries(0);

      assertThat(delivery.getTo()).isEqualTo("anne@acme.dev");
      assertThat(delivery.getKind()).isEqualTo("email");
      assertThat(delivery.getIdentifier()).startsWith("identifiers/");
      assertThat(delivery.getCode()).matches("[0-9]{6}");
      assertThat(flow.getChallenges(0).getParameters().unpack(CodeSent.class).getIdentifier())
          .isEqualTo(delivery.getIdentifier());
      Flow done = pyrite.answer(flow, code(delivery.getCode()));
      assertThat(done.getResult().getIdentifiers(0).getVerified()).isTrue();
    }

    @Test
    @DisplayName("a wrong code verifies nothing")
    void wrongCode() throws Exception {
      Flow flow = pyrite.start(ANNE, "verify", "anne@acme.dev");

      assertThat(status(() -> pyrite.answer(flow, code(wrong(flow.getDeliveries(0).getCode())))))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> pyrite.answer(flow, code("")))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a code expires after its lifetime")
    void expired() throws Exception {
      Flow flow = pyrite.start(ANNE, "verify", "anne@acme.dev");
      pyrite.clock.move(Duration.ofMinutes(11));

      assertThat(status(() -> pyrite.answer(flow, code(flow.getDeliveries(0).getCode()))))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a code of another flow is wrong")
    void otherFlow() throws Exception {
      Flow first = pyrite.start(ANNE, "verify", "anne@acme.dev");
      Flow second = pyrite.start(ANNE, "verify", "anne@acme.dev");

      if (!first.getDeliveries(0).getCode().equals(second.getDeliveries(0).getCode())) {
        assertThat(status(() -> pyrite.answer(second, code(first.getDeliveries(0).getCode()))))
            .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      }
    }

    @Test
    @DisplayName("an account verifies only an address of its own")
    void otherAddress() throws Exception {
      pyrite.account("bob", "bob@acme.dev", "another long passphrase");

      assertThat(status(() -> pyrite.start(ANNE, "verify", "bob@acme.dev"))).isEqualTo(GrpcStatus.NOT_FOUND);
      assertThat(status(() -> pyrite.start(ANNE, "verify", "nobody@acme.dev"))).isEqualTo(GrpcStatus.NOT_FOUND);
    }
  }

  @Nested
  @DisplayName("Recovery")
  class Recovery {

    @BeforeEach
    void verified() throws Exception {
      pyrite.verify(ANNE, "anne@acme.dev");
    }

    @Test
    @DisplayName("an address that is not verified gets no recovery code")
    void unverified() throws Exception {
      pyrite.authentication.addIdentifier(AddIdentifierRequest.newBuilder().setSubject(ANNE).setAccount("accounts/anne")
          .setKind("email").setValue("anne@home.example").build()).await(TIMEOUT);

      Flow recovery = pyrite.start("", "recover", "anne@home.example");

      assertThat(recovery.getDeliveriesList()).isEmpty();
      assertThat(status(() -> pyrite.answer(recovery, code("000000")))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("a recovery sets a new password and ends every session")
    void recovers() throws Exception {
      String session = pyrite.signIn("anne@acme.dev", PASSWORD);

      Flow recovery = pyrite.start("", "recover", "anne@acme.dev");
      Flow chosen = pyrite.answer(recovery, code(recovery.getDeliveries(0).getCode()));
      pyrite.answer(chosen, PasswordAnswer.newBuilder().setPassword("a brand new passphrase").build());

      assertThat(status(() -> pyrite.authenticate(session))).isEqualTo(GrpcStatus.UNAUTHENTICATED);
      assertThat(status(() -> pyrite.signIn("anne@acme.dev", PASSWORD))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(pyrite.signIn("anne@acme.dev", "a brand new passphrase")).isNotEmpty();
    }

    @Test
    @DisplayName("a recovery does not enrol a password before the code is proven")
    void codeFirst() throws Exception {
      Flow recovery = pyrite.start("", "recover", "anne@acme.dev");

      assertThat(status(() -> pyrite.answer(recovery, PasswordAnswer.newBuilder().setPassword(
          "a brand new passphrase").build()))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(pyrite.signIn("anne@acme.dev", PASSWORD)).isNotEmpty();
    }

    @Test
    @DisplayName("a wrong code leaves the password as it was")
    void wrongCode() throws Exception {
      Flow recovery = pyrite.start("", "recover", "anne@acme.dev");

      assertThat(status(() -> pyrite.answer(recovery, code(wrong(recovery.getDeliveries(0).getCode())))))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(pyrite.signIn("anne@acme.dev", PASSWORD)).isNotEmpty();
    }

    @Test
    @DisplayName("a recovery of an unknown address sends nothing and takes no code")
    void unknown() throws Exception {
      Flow recovery = pyrite.start("", "recover", "nobody@acme.dev");

      assertThat(recovery.getDeliveriesList()).isEmpty();
      for (String guess : new String[] { "000000", "123456", "999999" }) {
        assertThat(status(() -> pyrite.answer(recovery, code(guess)))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      }
    }

    @Test
    @DisplayName("guessing the code exhausts the flow")
    void guessing() throws Exception {
      Flow recovery = pyrite.start("", "recover", "anne@acme.dev");
      String real = recovery.getDeliveries(0).getCode();
      for (int attempt = 0; attempt < 3; attempt++) {
        assertThat(status(() -> pyrite.answer(recovery, code(wrong(real))))).isEqualTo(
            GrpcStatus.PERMISSION_DENIED);
      }

      assertThat(status(() -> pyrite.answer(recovery, code(real)))).isEqualTo(GrpcStatus.RESOURCE_EXHAUSTED);
    }
  }
}
