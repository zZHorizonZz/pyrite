package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectRetryReplay;
import dev.horizon.pyrite.conformance.condition.StartFlow;

@Summary("A retry with the same request id gives the first response, and signs in once.")
final class AnswerRetry extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectRetryReplay(PASSWORD), Requirement.FLOW_RETRY);
  }
}
