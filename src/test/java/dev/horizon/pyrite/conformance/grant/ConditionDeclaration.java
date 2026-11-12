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

@Summary("A condition is one that the declaration declares.")
final class ConditionDeclaration extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new GrantWithCondition(ANNE, "projects/ledger", "editor", BOB,
            "on_weekdays", Map.of())),
        Requirement.GRANT_CONDITION);
  }
}
