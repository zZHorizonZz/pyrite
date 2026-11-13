package dev.horizon.pyrite.conformance.handler;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddCodeRefusal;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An interceptor that cannot send a code stops the exchange.")
final class CodeRefusal extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddCodeRefusal(GrpcStatus.UNAVAILABLE));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.UNAVAILABLE, new StartExchange(ANNE, "verify", EMAIL)),
        Requirement.HANDLER_CODE);
  }
}
