package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A flow that takes the account needs a subject that is an account.")
final class AccountSubject extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartFlow("", "enrol-password", "")),
        Requirement.FLOW_SUBJECT);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartFlow("group:finance",
        "enrol-password", "")), Requirement.FLOW_SUBJECT);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new StartFlow("user:mallory",
        "enrol-password", "")), Requirement.FLOW_SUBJECT);
  }
}
