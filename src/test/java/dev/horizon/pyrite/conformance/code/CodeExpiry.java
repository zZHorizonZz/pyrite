package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerCode;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.MakeFlowOlder;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;

@Summary("A code expires after its lifetime.")
final class CodeExpiry extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow(ANNE, "verify", EMAIL));
    call(new MakeFlowOlder(Duration.ofMinutes(11)));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerCode()), Requirement.CODE_EXPIRY);
  }
}
