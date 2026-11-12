package dev.horizon.pyrite.conformance.declaration;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.ReadDeclaration;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An unknown id is not found.")
final class UnknownDeclaration extends ConformanceModule {

  @Override
  protected void run() {
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new ReadDeclaration("00000000-0000-0000-0000-000000000000")),
        Requirement.DECLARATION_READ);
  }
}
