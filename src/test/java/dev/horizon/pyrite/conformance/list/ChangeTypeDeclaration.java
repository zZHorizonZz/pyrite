package dev.horizon.pyrite.conformance.list;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.ListChanges;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A type that is not declared is refused.")
final class ChangeTypeDeclaration extends AccessModule {

  @Override
  protected void run() {
    prepareGroups();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new ListChanges(ANNE, "acme.dev/Folder")),
        Requirement.CHANGE_LIST);
  }
}
