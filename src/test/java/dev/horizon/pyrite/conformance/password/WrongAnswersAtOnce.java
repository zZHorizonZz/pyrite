package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.AnswerWrongPasswordsAtOnce;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Wrong answers at once count each, so that guessing in parallel passes no more than the most answers.")
final class WrongAnswersAtOnce extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in", EMAIL));
    callAndStopOnFailure(new AnswerWrongPasswordsAtOnce(10), Requirement.EXCHANGE_ANSWERS);
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.RESOURCE_EXHAUSTED, new AnswerPassword(PASSWORD)),
        Requirement.EXCHANGE_ANSWERS);
  }
}
