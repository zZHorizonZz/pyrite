package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectIdentifierValues;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.ListIdentifiers;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("An account lists its own identifiers to itself only.")
final class IdentifierList extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccountOfKind("mallory", "HUMAN"));
    callAndStopOnFailure(new ListIdentifiers(ANNE, "accounts/anne"), Requirement.IDENTIFIER_LIST);
    callAndContinueOnFailure(new ExpectIdentifierValues(List.of(EMAIL)), Requirement.IDENTIFIER_LIST);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new ListIdentifiers("user:mallory",
        "accounts/anne")), Requirement.IDENTIFIER_LIST);
  }
}
