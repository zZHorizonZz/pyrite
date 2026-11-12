package dev.horizon.pyrite.conformance.declaration;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.DeclarationChange;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.WriteDeclaration;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A declaration without the types that its flows need is refused.")
final class DeclarationWithoutAccount extends ConformanceModule {

  @Override
  protected void run() {
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new WriteDeclaration(DeclarationChange.FLOWS_ONLY)),
        Requirement.DECLARATION_RULES);
  }
}
