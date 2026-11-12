package dev.horizon.pyrite.conformance.handler;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddRefusal;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.Moment;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An interceptor of the end can stop the flow with its own status.")
final class FlowEndRefusal extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new AddRefusal(Moment.END, GrpcStatus.FAILED_PRECONDITION));
    call(new StartFlow("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new AnswerPassword(PASSWORD)),
        Requirement.HANDLER_FLOW_END);
  }
}
