package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantWithCondition;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Map;

@Summary("A condition's values have the types of its parameters.")
final class ConditionParameter extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new GrantWithCondition(ANNE, "projects/ledger", "editor",
            BOB, "not_expired", Map.of("expires_at", "next week"))),
        Requirement.GRANT_CONDITION);
    callAndContinueOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new GrantWithCondition(ANNE, "projects/ledger", "editor",
            BOB, "not_expired", Map.of("budget", 3))),
        Requirement.GRANT_CONDITION);
  }
}
