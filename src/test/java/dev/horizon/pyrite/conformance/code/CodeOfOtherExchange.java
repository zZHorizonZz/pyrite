package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.ExpectForeignCodeRefusal;
import dev.horizon.pyrite.conformance.condition.KeepExchange;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("A code of another exchange is wrong.")
final class CodeOfOtherExchange extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "verify", EMAIL));
    call(new KeepExchange(Value.OTHER_EXCHANGE));
    call(new StartExchange(ANNE, "verify", EMAIL));
    callAndStopOnFailure(new ExpectForeignCodeRefusal(Value.OTHER_EXCHANGE), Requirement.CODE_PROOF);
  }
}
