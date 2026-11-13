package dev.horizon.pyrite.conformance.declaration;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectLatestDeclaration;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.ReadDeclaration;
import dev.horizon.pyrite.conformance.condition.TypeChange;
import dev.horizon.pyrite.conformance.condition.WriteTypeChange;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A declaration that changes what finds the known resources of a type is refused, and one that changes a type"
    + " without resources is written.")
final class ResourceReach extends ConformanceModule {

  @Override
  protected void run() {
    call(new CreateObject(ANNE, "organizations/acme", ""));
    call(new CreateObject(ANNE, "projects/ledger", "organizations/acme"));
    call(new ReadDeclaration(""));
    for (TypeChange change : List.of(TypeChange.PROJECT_DROP, TypeChange.PROJECT_PLURAL, TypeChange.PROJECT_SINGULAR,
        TypeChange.ORGANIZATION_PARENTS)) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new WriteTypeChange(change)),
          Requirement.DECLARATION_REACH);
    }
    callAndStopOnFailure(new ExpectLatestDeclaration(false), Requirement.DECLARATION_REACH);
    callAndStopOnFailure(new WriteTypeChange(TypeChange.ROLE_PLURAL), Requirement.DECLARATION_REACH);
    callAndStopOnFailure(new ExpectLatestDeclaration(true), Requirement.DECLARATION_REACH);
  }
}
