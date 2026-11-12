package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerCodeText;
import dev.horizon.pyrite.conformance.condition.AnswerWrongCode;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A wrong code verifies nothing.")
final class WrongCode extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow(ANNE, "verify", EMAIL));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerWrongCode()), Requirement.CODE_PROOF);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerCodeText("")), Requirement.CODE_PROOF);
  }
}
