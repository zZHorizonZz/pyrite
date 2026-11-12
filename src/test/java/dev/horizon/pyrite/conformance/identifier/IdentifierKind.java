package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("An identifier's kind is one that the declaration names.")
final class IdentifierKind extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (String kind : List.of("EMAIL", "passport")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AddIdentifier(ANNE, "accounts/anne",
          kind, EMAIL)), Requirement.IDENTIFIER_FORMAT);
    }
  }
}
