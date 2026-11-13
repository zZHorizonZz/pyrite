package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A wrong password keeps the exchange at its revision, so the right one still signs in.")
final class WrongPasswordRetry extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerPassword("not the password")),
        Requirement.EXCHANGE_WRONG);
    callAndStopOnFailure(new AnswerPassword(PASSWORD), Requirement.EXCHANGE_WRONG);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.EXCHANGE_WRONG);
  }
}
