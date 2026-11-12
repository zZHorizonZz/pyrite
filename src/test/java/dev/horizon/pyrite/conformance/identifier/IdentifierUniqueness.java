package dev.horizon.pyrite.conformance.identifier;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("An address belongs to one account.")
final class IdentifierUniqueness extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccountOfKind("mallory", "HUMAN"));
    for (String address : List.of(EMAIL, "ANNE@acme.dev")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ALREADY_EXISTS, new AddIdentifier("user:mallory",
          "accounts/mallory", "email", address)), Requirement.IDENTIFIER_UNIQUE);
    }
  }
}
