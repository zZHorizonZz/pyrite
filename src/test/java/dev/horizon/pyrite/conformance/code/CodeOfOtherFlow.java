package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.ExpectForeignCodeRefusal;
import dev.horizon.pyrite.conformance.condition.KeepFlow;
import dev.horizon.pyrite.conformance.condition.StartFlow;

@Summary("A code of another flow is wrong.")
final class CodeOfOtherFlow extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow(ANNE, "verify", EMAIL));
    call(new KeepFlow(Value.OTHER_FLOW));
    call(new StartFlow(ANNE, "verify", EMAIL));
    callAndStopOnFailure(new ExpectForeignCodeRefusal(Value.OTHER_FLOW), Requirement.CODE_PROOF);
  }
}
