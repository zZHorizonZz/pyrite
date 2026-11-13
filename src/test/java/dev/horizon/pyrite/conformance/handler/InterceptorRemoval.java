package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddAndRemoveRecorder;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectInterceptions;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("An interceptor that is removed no longer runs.")
final class InterceptorRemoval extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddAndRemoveRecorder(Moment.END));
    call(new StartExchange("", "sign-in", EMAIL));
    call(new AnswerPassword(PASSWORD));
    callAndStopOnFailure(new ExpectInterceptions(0, "sign-in"), Requirement.HANDLER_REMOVE);
  }
}
