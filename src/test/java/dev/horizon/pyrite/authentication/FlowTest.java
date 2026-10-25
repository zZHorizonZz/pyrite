package dev.horizon.pyrite.authentication;

import static dev.horizon.pyrite.Pyrite.ANNE;
import static dev.horizon.pyrite.Pyrite.status;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import dev.horizon.pyrite.PyriteTest;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlowTest extends PyriteTest {

  private static final String PASSWORD = "correct horse battery staple";

  @BeforeEach
  void open() throws Exception {
    pyrite.accounts();
    pyrite.account("anne", "anne@acme.dev", PASSWORD);
  }

  private ContinueFlowRequest.Builder request(Flow flow, Any answer) {
    return ContinueFlowRequest.newBuilder().setFlow(flow.getName()).setRevision(flow.getRevision())
        .setRequestId(UUID.randomUUID().toString()).setAnswer(answer);
  }

  private Flow send(ContinueFlowRequest request) throws Exception {
    return pyrite.authentication.continueFlow(request).await(TIMEOUT);
  }

  private static Any password(String password) {
    return Any.pack(PasswordAnswer.newBuilder().setPassword(password).build());
  }

  @Nested
  @DisplayName("Starting")
  class Starting {

    @Test
    @DisplayName("a flow names a declared definition")
    void undeclared() {
      assertThat(status(() -> pyrite.start("", "sign-up", "anne@acme.dev"))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.start("", "", "anne@acme.dev"))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("a flow that takes an identifier needs one, whose kind the declaration's matches find when the request"
        + " names none")
    void identifier() throws Exception {
      assertThat(status(() -> pyrite.start("", "sign-in", ""))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      Flow started = pyrite.authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("sign-in")
          .setIdentifier("Anne@Acme.dev").build()).await(TIMEOUT);
      assertThat(pyrite.answer(started, PasswordAnswer.newBuilder().setPassword(PASSWORD).build()).getResult().getAccount())
          .isEqualTo("accounts/anne");
    }

    @Test
    @DisplayName("a flow that takes the account needs a subject that is an account")
    void subject() {
      assertThat(status(() -> pyrite.start("", "enrol-password", ""))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.start("group:finance", "enrol-password", "")))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> pyrite.start("user:mallory", "enrol-password", ""))).isEqualTo(GrpcStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a flow of a phone number takes a valid number")
    void phone() {
      assertThat(status(() -> pyrite.authentication.startFlow(StartFlowRequest.newBuilder().setDefinition("sign-in")
          .setIdentifierKind("phone").setIdentifier("not a number").build()).await(TIMEOUT)))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Answers")
  class Answers {

    @Test
    @DisplayName("an answer names the revision of the flow, and a stale one is aborted")
    void revision() throws Exception {
      Flow flow = pyrite.start("", "sign-in-with-two-factors", "anne@acme.dev");
      pyrite.answer(flow, PasswordAnswer.newBuilder().setPassword(PASSWORD).build());

      assertThat(status(() -> send(request(flow, password(PASSWORD)).build()))).isEqualTo(GrpcStatus.ABORTED);
      assertThat(status(() -> send(request(flow, password(PASSWORD)).setRevision(flow.getRevision() + 5).build())))
          .isEqualTo(GrpcStatus.ABORTED);
    }

    @Test
    @DisplayName("a retry with the same request id gives the first result, and signs in once")
    void retry() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");
      ContinueFlowRequest request = request(flow, password(PASSWORD)).build();

      Flow first = send(request);
      Flow retried = send(request);

      assertThat(retried).isEqualTo(first);
    }

    @Test
    @DisplayName("an answer that the stage does not take is refused")
    void notTaken() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");

      assertThat(status(() -> pyrite.answer(flow, CodeAnswer.newBuilder().setCode("123456").build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> send(request(flow, Any.pack(StringValue.of(PASSWORD))).build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    @DisplayName("an answer that does not parse as its message is refused")
    void garbage() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");
      Any broken = Any.newBuilder().setTypeUrl("type.googleapis.com/pyrite.authentication.v1.PasswordAnswer")
          .setValue(ByteString.copyFrom(new byte[] { (byte) 0xff, (byte) 0xff, 0x01 })).build();

      assertThat(status(() -> send(request(flow, broken).build()))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
      assertThat(status(() -> send(request(flow, Any.getDefaultInstance()).build())))
          .isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Lifetime")
  class Lifetime {

    @Test
    @DisplayName("a done flow takes no more answers")
    void done() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");
      Flow finished = pyrite.answer(flow, PasswordAnswer.newBuilder().setPassword(PASSWORD).build());

      assertThat(status(() -> pyrite.answer(finished, PasswordAnswer.newBuilder().setPassword(PASSWORD).build())))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @Test
    @DisplayName("an expired flow takes no answer, not even the right one")
    void expired() throws Exception {
      Flow flow = pyrite.start("", "sign-in", "anne@acme.dev");
      pyrite.clock.move(Duration.ofMinutes(16));

      assertThat(status(() -> pyrite.answer(flow, PasswordAnswer.newBuilder().setPassword(PASSWORD).build())))
          .isEqualTo(GrpcStatus.FAILED_PRECONDITION);
    }

    @ParameterizedTest
    @ValueSource(strings = { "flows/00000000-0000-0000-0000-000000000000", "flows/nope" })
    @DisplayName("an unknown flow is not found")
    void unknown(String name) {
      assertThat(status(() -> send(ContinueFlowRequest.newBuilder().setFlow(name).setRevision(1)
          .setRequestId("r").setAnswer(password(PASSWORD)).build()))).isIn(GrpcStatus.NOT_FOUND,
              GrpcStatus.INVALID_ARGUMENT);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "sessions/abc", "accounts/anne", "flows" })
    @DisplayName("a name that is not a flow's is refused")
    void malformed(String name) {
      assertThat(status(() -> send(ContinueFlowRequest.newBuilder().setFlow(name).setRevision(1)
          .setRequestId("r").setAnswer(password(PASSWORD)).build()))).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }
  }

  @Nested
  @DisplayName("Factors")
  class Factors {

    @Test
    @DisplayName("a flow that enrols a factor names it and its account in its result")
    void reported() throws Exception {
      Flow done = pyrite.answer(pyrite.start(ANNE, "enrol-password", ""), PasswordAnswer.newBuilder()
          .setPassword("another correct horse battery").build());

      assertThat(done.getResult().getAccount()).isEqualTo("accounts/anne");
      assertThat(done.getResult().getFactorsList()).singleElement().satisfies(factor -> {
        assertThat(factor.getKind()).isEqualTo(Factor.Kind.PASSWORD);
        assertThat(factor.getAccount()).isEqualTo("accounts/anne");
      });
    }
  }
}
