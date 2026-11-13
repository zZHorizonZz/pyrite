package dev.horizon.pyrite.conformance.exchange;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectResultAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An exchange that takes an identifier needs one; the matches of the declaration find its kind when none is named.")
final class IdentifierKindMatch extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartExchange("", "sign-in", "")),
        Requirement.EXCHANGE_IDENTIFIER);
    callAndStopOnFailure(new StartExchange("", "sign-in", "", "Anne@Acme.dev"), Requirement.EXCHANGE_IDENTIFIER);
    call(new AnswerPassword(PASSWORD));
    callAndStopOnFailure(new ExpectResultAccount("accounts/anne"), Requirement.EXCHANGE_IDENTIFIER);
  }
}
