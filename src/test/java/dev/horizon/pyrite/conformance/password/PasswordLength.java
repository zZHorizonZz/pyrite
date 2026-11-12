package dev.horizon.pyrite.conformance.password;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignIn;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A password has 8 to 128 characters.")
final class PasswordLength extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (int length : List.of(0, 1, 7, 129, 1000)) {
      call(new StartFlow(ANNE, "enrol-password", ""));
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerPassword("x".repeat(length))),
          Requirement.PASSWORD_LENGTH);
    }
    for (int length : List.of(8, 128)) {
      call(new StartFlow(ANNE, "enrol-password", ""));
      callAndContinueOnFailure(new AnswerPassword("p".repeat(length)), Requirement.PASSWORD_LENGTH);
      callAndContinueOnFailure(new SignIn(EMAIL, "p".repeat(length)), Requirement.PASSWORD_LENGTH);
    }
  }
}
