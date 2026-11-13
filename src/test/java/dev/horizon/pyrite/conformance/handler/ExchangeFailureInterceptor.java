package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddRecorder;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectInterceptions;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An interceptor of failures hears of a wrong answer, and the answer still fails.")
final class ExchangeFailureInterceptor extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddRecorder(Moment.FAILURE));
    call(new StartExchange("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerPassword("a wrong password")),
        Requirement.HANDLER_EXCHANGE_FAILURE);
    callAndStopOnFailure(new ExpectInterceptions(1, "sign-in"), Requirement.HANDLER_EXCHANGE_FAILURE);
  }
}
