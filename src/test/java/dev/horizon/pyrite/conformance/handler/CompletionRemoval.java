package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectEmptyResult;
import dev.horizon.pyrite.conformance.condition.RemoveCompletion;
import dev.horizon.pyrite.conformance.condition.StartFlow;

@Summary("Without the completion handler, a flow ends without the steps that it declares.")
final class CompletionRemoval extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new RemoveCompletion());
    call(new StartFlow("", "sign-in", EMAIL));
    callAndStopOnFailure(new AnswerPassword(PASSWORD), Requirement.HANDLER_COMPLETION);
    callAndStopOnFailure(new ExpectEmptyResult(), Requirement.HANDLER_COMPLETION);
  }
}
