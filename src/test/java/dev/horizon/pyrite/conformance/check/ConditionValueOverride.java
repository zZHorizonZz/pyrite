package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantWithCondition;
import java.util.Map;

@Summary("A check cannot override the values that the grant holds.")
final class ConditionValueOverride extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantWithCondition(ANNE, "projects/ledger", "editor", BOB, "not_expired", Map.of("expires_at",
        "2026-06-01T00:00:00Z")));
    call(new Check(ANNE, BOB, "projects/ledger", "can_edit", Map.of("current_time", "2026-07-01T00:00:00Z",
        "expires_at", "2030-01-01T00:00:00Z")));
    callAndStopOnFailure(new ExpectCheckAnswer(false), Requirement.ACCESS_CONDITION);
  }
}
