package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerAtRevision;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An answer names the revision of the flow, and a stale one is aborted.")
final class StaleRevision extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow("", "sign-in-with-two-factors", EMAIL));
    call(new AnswerPassword(PASSWORD));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ABORTED, new AnswerAtRevision(0, PASSWORD)),
        Requirement.FLOW_REVISION);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ABORTED, new AnswerAtRevision(5, PASSWORD)),
        Requirement.FLOW_REVISION);
  }
}
