package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.SignIn;
import dev.horizon.pyrite.conformance.condition.ThrowOnSessionIssue;

@Summary("A handler that throws does not change what happened.")
final class ThrowingHandler extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new ThrowOnSessionIssue());
    callAndStopOnFailure(new SignIn(EMAIL, PASSWORD), Requirement.HANDLER_THROW);
  }
}
