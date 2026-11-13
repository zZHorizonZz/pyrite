package dev.horizon.pyrite.conformance.identifier;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.AnswerCode;
import dev.horizon.pyrite.conformance.condition.Authenticate;
import dev.horizon.pyrite.conformance.condition.CreateAccountOfKind;
import dev.horizon.pyrite.conformance.condition.ExpectPrincipalAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignIn;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Another account adds a verified address, cannot verify it, and takes none of its sign-ins.")
final class IdentifierUniqueness extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "verify", EMAIL));
    call(new AnswerCode());
    call(new CreateAccountOfKind("mallory", "HUMAN"));
    callAndContinueOnFailure(new AddIdentifier("user:mallory", "accounts/mallory", "email", "ANNE@acme.dev"),
        Requirement.IDENTIFIER_UNIQUE);
    call(new StartExchange("user:mallory", "verify", EMAIL));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ALREADY_EXISTS, new AnswerCode()),
        Requirement.IDENTIFIER_UNIQUE);
    callAndStopOnFailure(new SignIn(EMAIL, PASSWORD), Requirement.IDENTIFIER_UNIQUE);
    callAndStopOnFailure(new Authenticate(), Requirement.IDENTIFIER_UNIQUE);
    callAndStopOnFailure(new ExpectPrincipalAccount("accounts/anne"), Requirement.IDENTIFIER_UNIQUE);
  }
}
