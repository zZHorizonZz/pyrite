package dev.horizon.pyrite.conformance.declaration;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.DeclarationChange;
import dev.horizon.pyrite.conformance.condition.ExpectLatestDeclaration;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.ReadDeclaration;
import dev.horizon.pyrite.conformance.condition.WriteDeclaration;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A declaration that breaks a rule is refused, and the latest stays.")
final class DeclarationRefusal extends ConformanceModule {

  @Override
  protected void run() {
    call(new ReadDeclaration(""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new WriteDeclaration(DeclarationChange.EMPTY_TYPE)),
        Requirement.DECLARATION_RULES);
    callAndStopOnFailure(new ExpectLatestDeclaration(false), Requirement.DECLARATION_RULES);
  }
}
