package dev.horizon.pyrite.conformance.check;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.Check;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAnswer;
import dev.horizon.pyrite.conformance.condition.GrantWithCondition;
import java.util.List;
import java.util.Map;

@Summary("A condition holds until it no longer does.")
final class ConditionHolding extends AccessModule {

  @Override
  protected void run() {
    prepareLedger();
    call(new GrantWithCondition(ANNE, "projects/ledger", "editor", BOB, "not_expired", Map.of("expires_at",
        "2026-06-01T00:00:00Z")));
    for (String time : List.of("2026-05-01T00:00:00Z", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z")) {
      call(new Check(ANNE, BOB, "projects/ledger", "can_edit", Check.at(time)));
      callAndContinueOnFailure(new ExpectCheckAnswer(time.startsWith("2026-05")), Requirement.ACCESS_CONDITION);
    }
  }
}
