package dev.horizon.pyrite.conformance.exchange;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An exchange that takes the account needs a subject that is an account.")
final class AccountSubject extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartExchange("", "enrol-password", "")),
        Requirement.EXCHANGE_SUBJECT);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartExchange("group:finance",
        "enrol-password", "")), Requirement.EXCHANGE_SUBJECT);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.NOT_FOUND, new StartExchange("user:mallory",
        "enrol-password", "")), Requirement.EXCHANGE_SUBJECT);
  }
}
