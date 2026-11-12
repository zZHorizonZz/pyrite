package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.ExpectIdentifier;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An identifier that names no kind takes the first kind whose match it meets.")
final class IdentifierWithoutKind extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndContinueOnFailure(new AddIdentifier(ANNE, "accounts/anne", "", " Anne.Smith@Example.COM "),
        Requirement.IDENTIFIER_FORMAT);
    callAndContinueOnFailure(new ExpectIdentifier("email", "anne.smith@example.com", "accounts/anne"),
        Requirement.IDENTIFIER_FORMAT);
    callAndContinueOnFailure(new AddIdentifier(ANNE, "accounts/anne", "", "Anne_Smith"), Requirement.IDENTIFIER_FORMAT);
    callAndContinueOnFailure(new ExpectIdentifier("username", "anne_smith", "accounts/anne"), Requirement.IDENTIFIER_FORMAT);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AddIdentifier(ANNE, "accounts/anne",
        "", "!!")), Requirement.IDENTIFIER_FORMAT);
  }
}
