package dev.horizon.pyrite.conformance.object;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccessModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateObject;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A name is an object that OpenFGA takes, of a declared type.")
final class ObjectName extends AccessModule {

  private static final String ORGANIZATION = "acme.dev/Organization:";

  @Override
  protected void run() {
    call(new CreateObject(ANNE, ORGANIZATION + "organizations/acme", ""));
    for (String name : List.of("acme.dev/Folder:folders/docs", "organizations/globex", "acme.dev/Organization",
        ORGANIZATION, ORGANIZATION + "*", ORGANIZATION + "organizations/globex:v2", ORGANIZATION + "organizations/globex#v2",
        ORGANIZATION + "organizations/Globex Corp", ORGANIZATION + "organizations/globex\tcorp",
        ORGANIZATION + "organizations/globex\n", ORGANIZATION + "organizations/globex\u0007",
        ORGANIZATION + "o".repeat(256 - ORGANIZATION.length() + 1), "t".repeat(255) + ":organizations/globex",
        ":organizations/globex", "acme.dev/Organization :organizations/globex")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new CreateObject(ANNE, name, "")),
          Requirement.OBJECT_NAME);
    }
  }
}
