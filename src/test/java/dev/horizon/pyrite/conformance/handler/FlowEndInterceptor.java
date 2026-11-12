package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddRecorder;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectFlowEnd;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.StartFlow;

@Summary("An interceptor of the end runs after the declared steps, with the result that they filled in.")
final class FlowEndInterceptor extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddRecorder(Moment.END));
    call(new StartFlow("", "sign-in", EMAIL));
    callAndStopOnFailure(new AnswerPassword(PASSWORD), Requirement.HANDLER_FLOW_END);
    callAndContinueOnFailure(new ExpectFlowEnd("sign-in", "accounts/anne"), Requirement.HANDLER_FLOW_END);
    callAndContinueOnFailure(new ExpectSessionIssue(), Requirement.HANDLER_FLOW_END);
  }
}
