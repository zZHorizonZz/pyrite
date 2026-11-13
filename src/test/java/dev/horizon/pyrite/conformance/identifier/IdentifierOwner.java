package dev.horizon.pyrite.conformance.identifier;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An identifier is added only to an account that the subject administers.")
final class IdentifierOwner extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccountOfKind("mallory", "HUMAN"));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AddIdentifier("user:mallory",
        "accounts/anne", "email", "mallory@evil.dev")), Requirement.IDENTIFIER_OWNER);
  }
}
