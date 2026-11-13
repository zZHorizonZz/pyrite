package dev.horizon.pyrite.conformance.exchange;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerAtRevision;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An answer names the revision of the exchange, and a stale one is aborted.")
final class StaleRevision extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in-with-two-factors", EMAIL));
    call(new AnswerPassword(PASSWORD));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ABORTED, new AnswerAtRevision(0, PASSWORD)),
        Requirement.EXCHANGE_REVISION);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.ABORTED, new AnswerAtRevision(5, PASSWORD)),
        Requirement.EXCHANGE_REVISION);
  }
}
