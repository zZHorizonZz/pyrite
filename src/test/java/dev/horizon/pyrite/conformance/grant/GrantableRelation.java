package dev.horizon.pyrite.conformance.grant;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.GrantRelation;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("Only a grantable relation is granted.")
final class GrantableRelation extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    for (String relation : List.of("can_view", "can_edit", "flies", "parent", "")) {
      callAndContinueOnFailure(
          new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new GrantRelation(ANNE, "projects/ledger", relation,
              BOB)),
          Requirement.GRANT_RELATION);
    }
  }
}
