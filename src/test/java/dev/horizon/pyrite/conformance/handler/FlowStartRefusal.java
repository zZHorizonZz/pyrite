package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddRefusal;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An interceptor of the start can refuse a flow.")
final class FlowStartRefusal extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddRefusal(Moment.START, GrpcStatus.PERMISSION_DENIED));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new StartFlow("", "sign-in", EMAIL)),
        Requirement.HANDLER_FLOW_START);
  }
}
