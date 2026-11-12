package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.MakeFlowOlder;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("An expired flow takes no answer, not even the right one.")
final class FlowExpiry extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow("", "sign-in", EMAIL));
    call(new MakeFlowOlder(Duration.ofMinutes(16)));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new AnswerPassword(PASSWORD)),
        Requirement.FLOW_EXPIRY);
  }
}
