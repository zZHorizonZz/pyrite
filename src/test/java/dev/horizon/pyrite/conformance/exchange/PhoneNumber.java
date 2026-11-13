package dev.horizon.pyrite.conformance.exchange;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An exchange of a phone number takes a valid number.")
final class PhoneNumber extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new StartExchange("", "sign-in", "phone",
        "not a number")), Requirement.EXCHANGE_IDENTIFIER);
  }
}
