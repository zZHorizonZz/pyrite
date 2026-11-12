package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A flow names a declared definition.")
final class DefinitionDeclaration extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartFlow("", "sign-up", EMAIL)),
        Requirement.FLOW_DEFINITION);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartFlow("", "", EMAIL)),
        Requirement.FLOW_DEFINITION);
  }
}
