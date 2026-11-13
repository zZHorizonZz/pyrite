package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An exchange takes at most its answers, and then refuses even the right password.")
final class PasswordGuessing extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in", EMAIL));
    for (int attempt = 0; attempt < 3; attempt++) {
      callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerPassword("guess")),
          Requirement.EXCHANGE_ANSWERS);
    }
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.RESOURCE_EXHAUSTED, new AnswerPassword(PASSWORD)),
        Requirement.EXCHANGE_ANSWERS);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.RESOURCE_EXHAUSTED, new AnswerPassword("guess")),
        Requirement.EXCHANGE_ANSWERS);
  }
}
