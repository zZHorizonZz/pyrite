package dev.horizon.pyrite.conformance.flow;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A done flow takes no more answers.")
final class DoneFlow extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow("", "sign-in", EMAIL));
    call(new AnswerPassword(PASSWORD));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new AnswerPassword(PASSWORD)),
        Requirement.FLOW_DONE);
  }
}
