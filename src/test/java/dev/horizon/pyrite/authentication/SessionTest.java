package dev.horizon.pyrite.authentication;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.BOB;
import static dev.horizon.pyrite.Pyrite.START;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.authentication.v1.DeleteAccountRequest;
import dev.horizon.pyrite.authentication.v1.EndSessionRequest;
import dev.horizon.pyrite.authentication.v1.GetSessionRequest;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.authentication.v1.PurgeRequest;
import dev.horizon.pyrite.authentication.v1.Session;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SessionTest extends PyriteTest {

  private static final String PASSWORD = "correct horse battery staple";

  private String token;

  @BeforeEach
  void open() throws Exception {
    pyrite.accounts();
    pyrite.account("anne", "anne@acme.dev", PASSWORD);
    token = pyrite.signIn("anne@acme.dev", PASSWORD);
  }

  @Nested
  @DisplayName("Authenticate")
  class Authenticate {

    @Test
    @DisplayName("a token gives the principal, the account, the session and the expiry")
    void principal() throws Exception {
      Principal principal = pyrite.authenticate(token);

      assertThat(principal.getSubject()).isEqualTo(ANNE);
      assertThat(principal.getAccount()).isEqualTo("accounts/anne");
      assertThat(principal.getSession()).startsWith("sessions/");
      assertThat(principal.getExpireTime().getSeconds()).isEqualTo(START.plus(Duration.ofDays(30)).getEpochSecond());
    }

    @Test
    @DisplayName("each sign-in issues its own token")
    void distinct() throws Exception {
      assertThat(pyrite.signIn("anne@acme.dev", PASSWORD)).isNotEqualTo(token);
    }

    @Test
    @DisplayName("a request names a credential")
    void empty() {
      assertThat(status(() -> pyrite.authentication.authenticate(AuthenticateRequest.getDefaultInstance())
          .await(TIMEOUT))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Forged tokens")
  class ForgedTokens {

    @Test
    @DisplayName("a token with any character of its secret changed is refused")
    void secret() {
      int dot = token.indexOf('.');
      for (int index = dot + 1; index < token.length(); index++) {
        char changed = token.charAt(index) == 'A' ? 'B' : 'A';
        String forged = token.substring(0, index) + changed + token.substring(index + 1);
        assertThat(status(() -> pyrite.authenticate(forged))).as(forged).isEqualTo(GrpcStatus.UNAUTHENTICATED);
      }
    }

    @Test
    @DisplayName("a token cut short, or with its secret moved to another session, is refused")
    void moved() throws Exception {
      String other = pyrite.signIn("anne@acme.dev", PASSWORD);
      String swapped = other.substring(0, other.indexOf('.')) + token.substring(token.indexOf('.'));

      assertThat(status(() -> pyrite.authenticate(swapped))).isEqualTo(GrpcStatus.UNAUTHENTICATED);
      assertThat(status(() -> pyrite.authenticate(token.substring(0, token.length() - 1)))).isEqualTo(
          GrpcStatus.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("text that is not a token is refused")
    void malformed() {
      for (String forged : new String[] { "", ".", "x", "x.", ".x", "x.y", token.substring(0, token.indexOf('.')),
          "00000000-0000-0000-0000-000000000000." + token.substring(token.indexOf('.') + 1) }) {
        assertThat(status(() -> pyrite.authenticate(forged))).as(forged).isEqualTo(GrpcStatus.UNAUTHENTICATED);
      }
    }
  }

  @Nested
  @DisplayName("Expiry")
  class Expiry {

    @Test
    @DisplayName("an unused session ends after the idle lifetime")
    void idle() throws Exception {
      pyrite.clock.move(Duration.ofDays(7));

      assertThat(status(() -> pyrite.authenticate(token))).isEqualTo(GrpcStatus.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("each use extends the idle lifetime")
    void extended() throws Exception {
      for (int week = 0; week < 3; week++) {
        pyrite.clock.move(Duration.ofDays(6));
        assertThat(pyrite.authenticate(token).getSubject()).isEqualTo(ANNE);
      }
    }

    @Test
    @DisplayName("a used session still ends at its lifetime")
    void absolute() throws Exception {
      for (int week = 0; week < 4; week++) {
        pyrite.clock.move(Duration.ofDays(6));
        pyrite.authenticate(token);
      }
      pyrite.clock.move(Duration.ofDays(6));

      assertThat(status(() -> pyrite.authenticate(token))).isEqualTo(GrpcStatus.UNAUTHENTICATED);
    }
  }

  @Nested
  @DisplayName("Ending")
  class Ending {

    @Test
    @DisplayName("an ended session no longer authenticates")
    void ended() throws Exception {
      String session = pyrite.authenticate(token).getSession();
      pyrite.authentication.endSession(EndSessionRequest.newBuilder().setSubject(ANNE).setName(session).build())
          .await(TIMEOUT);

      assertThat(status(() -> pyrite.authenticate(token))).isEqualTo(GrpcStatus.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("a session of a deleted account no longer authenticates")
    void account() throws Exception {
      pyrite.authentication.deleteAccount(DeleteAccountRequest.newBuilder().setSubject(ANNE)
          .setName("accounts/anne").build()).await(TIMEOUT);

      assertThat(status(() -> pyrite.authenticate(token))).isEqualTo(GrpcStatus.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("a session is read by its account, and by nobody else")
    void read() throws Exception {
      String session = pyrite.authenticate(token).getSession();

      Session read = pyrite.authentication.getSession(GetSessionRequest.newBuilder().setSubject(ANNE)
          .setName(session).build()).await(TIMEOUT);

      assertThat(read.getAccount()).isEqualTo("accounts/anne");
      assertThat(read.getExpireTime()).isEqualTo(pyrite.authenticate(token).getExpireTime());
      assertThat(status(() -> pyrite.authentication.getSession(GetSessionRequest.newBuilder().setSubject(BOB)
          .setName(session).build()).await(TIMEOUT))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("another account does not end a session")
    void foreign() throws Exception {
      String session = pyrite.authenticate(token).getSession();

      assertThat(status(() -> pyrite.authentication.endSession(EndSessionRequest.newBuilder().setSubject(BOB)
          .setName(session).build()).await(TIMEOUT))).isEqualTo(GrpcStatus.PERMISSION_DENIED);
    }

    @Test
    @DisplayName("the purge deletes an ended session")
    void purged() throws Exception {
      String session = pyrite.authenticate(token).getSession();
      pyrite.authentication.endSession(EndSessionRequest.newBuilder().setSubject(ANNE).setName(session).build())
          .await(TIMEOUT);

      assertThat(pyrite.authentication.purge(PurgeRequest.getDefaultInstance()).await(TIMEOUT).getPurged())
          .isEqualTo(1);
    }
  }
}
