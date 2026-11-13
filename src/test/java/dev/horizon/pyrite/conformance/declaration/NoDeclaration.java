package dev.horizon.pyrite.conformance.declaration;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AuthenticateToken;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.ForgetDeclarations;
import dev.horizon.pyrite.conformance.condition.ReadDeclaration;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Before any declaration, reading finds none and every other call fails its precondition.")
final class NoDeclaration extends ConformanceModule {

  @Override
  protected void run() {
    call(new ForgetDeclarations());
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new ReadDeclaration("")), Requirement.DECLARATION_NONE);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new CreateObject(ANNE, "organizations/acme", "")),
        Requirement.DECLARATION_NONE);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new AuthenticateToken("x.y")),
        Requirement.DECLARATION_NONE);
  }
}
