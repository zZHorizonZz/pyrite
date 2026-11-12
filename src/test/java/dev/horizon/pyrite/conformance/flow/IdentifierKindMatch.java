package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectResultAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A flow that takes an identifier needs one, whose kind the declaration's matches find when the request names none.")
final class IdentifierKindMatch extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartFlow("", "sign-in", "")),
        Requirement.FLOW_IDENTIFIER);
    callAndStopOnFailure(new StartFlow("", "sign-in", "", "Anne@Acme.dev"), Requirement.FLOW_IDENTIFIER);
    call(new AnswerPassword(PASSWORD));
    callAndStopOnFailure(new ExpectResultAccount("accounts/anne"), Requirement.FLOW_IDENTIFIER);
  }
}
