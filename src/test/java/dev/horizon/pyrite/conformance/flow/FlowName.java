package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerFlowByName;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Set;

@Summary("An unknown flow is not found, and a name that is not a flow's is refused.")
final class FlowName extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (String unknown : List.of("flows/00000000-0000-0000-0000-000000000000", "flows/nope")) {
      callAndContinueOnFailure(new ExpectStatus(Set.of(GrpcStatus.NOT_FOUND, GrpcStatus.INVALID_ARGUMENT),
          new AnswerFlowByName(unknown, PASSWORD)), Requirement.FLOW_NAME);
    }
    for (String name : List.of("", "sessions/abc", "accounts/anne", "flows")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerFlowByName(name, PASSWORD)),
          Requirement.FLOW_NAME);
    }
  }
}
