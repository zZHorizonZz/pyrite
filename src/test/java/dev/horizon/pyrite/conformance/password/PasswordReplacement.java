package dev.horizon.pyrite.conformance.password;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignIn;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A new password replaces the old one.")
final class PasswordReplacement extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "enrol-password", ""));
    call(new AnswerPassword("a brand new passphrase"));
    callAndContinueOnFailure(new SignIn(EMAIL, "a brand new passphrase"), Requirement.PASSWORD_REPLACE);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new SignIn(EMAIL, PASSWORD)),
        Requirement.PASSWORD_REPLACE);
  }
}
