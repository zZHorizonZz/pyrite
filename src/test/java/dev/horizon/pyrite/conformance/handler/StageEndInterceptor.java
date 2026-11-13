package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddRecorder;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectInterceptions;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("An interceptor of stages runs when an answer meets a stage.")
final class StageEndInterceptor extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddRecorder(Moment.STAGE_END));
    call(new StartExchange("", "sign-in", EMAIL));
    call(new AnswerPassword(PASSWORD));
    callAndStopOnFailure(new ExpectInterceptions(1, "sign-in"), Requirement.HANDLER_STAGE_END);
  }
}
