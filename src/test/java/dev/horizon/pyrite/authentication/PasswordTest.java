package dev.horizon.pyrite.authentication;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.Pyrite.token;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import io.vertx.grpc.common.GrpcStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordTest extends PyriteTest {

  private static final String PASSWORD = "correct horse battery staple";

  @BeforeEach
  void open() throws Exception {
    pyrite.accounts();
    pyrite.account("anne", "anne@acme.dev", PASSWORD);
  }

  private static PasswordAnswer password(String password) {
    return PasswordAnswer.newBuilder().setPassword(password).build();
  }

  @Nested
  @DisplayName("Sign-in")
  class SignIn {

    @Test
    @DisplayName("the right password issues a session of the account")
    void right() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");
      assertThat(flow.getChallengesList()).extracting(Challenge::getMethod)
          .contains("pyrite.authentication.v1.PasswordAnswer");

      String token = token(pyrite.answer(flow, password(PASSWORD)));

      assertThat(pyrite.authenticate(token).getSubject()).isEqualTo(ANNE);
    }

    @Test
    @DisplayName("an email address signs in however it is cased or padded")
    void normalized() throws Exception {
      assertThat(pyrite.signIn("  Anne@ACME.dev ", PASSWORD)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Correct horse battery staple", "correct horse battery staple ", "correct horse",
        "correct horse battery stapl", "", "CORRECT HORSE BATTERY STAPLE" })
    @DisplayName("any other password is wrong")
    void wrong(String guess) throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");

      assertThat(status(() -> pyrite.answer(flow, password(guess)))).isIn(GrpcStatus.PERMISSION_DENIED,
          GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a wrong password keeps the flow at its revision, so the right one still signs in")
    void retry() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");

      assertThat(status(() -> pyrite.answer(flow, password("not the password"))))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(token(pyrite.answer(flow, password(PASSWORD)))).isNotEmpty();
    }
  }

  @Nested
  @DisplayName("Guessing")
  class Guessing {

    @Test
    @DisplayName("a flow takes at most its answers, and then refuses even the right password")
    void exhausted() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");
      for (int attempt = 0; attempt < 3; attempt++) {
        assertThat(status(() -> pyrite.answer(flow, password("guess")))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
      }

      assertThat(status(() -> pyrite.answer(flow, password(PASSWORD)))).isEqualTo(GrpcStatus.RESOURCE_EXHAUSTED);
      assertThat(status(() -> pyrite.answer(flow, password("guess")))).isEqualTo(GrpcStatus.RESOURCE_EXHAUSTED);
    }

    @Test
    @DisplayName("an unknown address takes the same answers, none of them right, and exhausts the same way")
    void unknown() throws Exception {
      Flow known = pyrite.start("", "sign-in", "anne@acme.dev");
      Flow unknown = pyrite.start("", "sign-in", "nobody@acme.dev");

      assertThat(unknown.getChallengesList()).extracting(Challenge::getMethod)
          .isEqualTo(known.getChallengesList().stream().map(Challenge::getMethod).toList());
      assertThat(unknown.getDeliveriesList()).isEmpty();
      for (int attempt = 0; attempt < 3; attempt++) {
        assertThat(status(() -> pyrite.answer(unknown, password(PASSWORD)))).isEqualTo(
            GrpcStatus.PERMISSION_DENIED);
      }
      assertThat(status(() -> pyrite.answer(unknown, password(PASSWORD)))).isEqualTo(
          GrpcStatus.RESOURCE_EXHAUSTED);
    }

    @Test
    @DisplayName("a password of another account does not sign in to this one")
    void otherAccount() throws Exception {
      pyrite.account("bob", "bob@acme.dev", "another long passphrase");

      assertThat(status(() -> pyrite.signIn("anne@acme.dev", "another long passphrase")))
          .isEqualTo(GrpcStatus.PERMISSION_DENIED);
      assertThat(status(() -> pyrite.signIn("bob@acme.dev", PASSWORD))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }
  }

  @Nested
  @DisplayName("Enrolment")
  class Enrolment {

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, 7, 129, 1000 })
    @DisplayName("a password has 8 to 128 characters")
    void length(int length) throws Exception {
      Flow flow = pyrite.start(ANNE, "enrol-password", "");

      assertThat(status(() -> pyrite.answer(flow, password("x".repeat(length)))))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @ValueSource(ints = { 8, 128 })
    @DisplayName("a password of 8 or of 128 characters is taken")
    void bounds(int length) throws Exception {
      pyrite.answer(pyrite.start(ANNE, "enrol-password", ""), password("p".repeat(length)));

      assertThat(pyrite.signIn("anne@acme.dev", "p".repeat(length))).isNotEmpty();
    }

    @Test
    @DisplayName("a new password replaces the old one")
    void replaces() throws Exception {
      pyrite.answer(pyrite.start(ANNE, "enrol-password", ""), password("a brand new passphrase"));

      assertThat(pyrite.signIn("anne@acme.dev", "a brand new passphrase")).isNotEmpty();
      assertThat(status(() -> pyrite.signIn("anne@acme.dev", PASSWORD))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("only the account itself enrols a password for it")
    void self() {
      assertThat(status(() -> pyrite.start("", "enrol-password", ""))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.start("user:mallory", "enrol-password", ""))).isIn(GrpcStatus.NOT_FOUND,
          GrpcStatus.INVALID_ARGUMENT, GrpcStatus.FAILED_PRECONDITION);
    }
  }
}
