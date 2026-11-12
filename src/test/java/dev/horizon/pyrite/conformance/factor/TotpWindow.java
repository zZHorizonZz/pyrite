package dev.horizon.pyrite.conformance.factor;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerTotp;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A code from one step either side is taken, and from two steps is not.")
final class TotpWindow extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolTotp();
    passPasswordStage();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerTotp(Value.TOTP_SECRET, -2)),
        Requirement.TOTP_WINDOW);
    passPasswordStage();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerTotp(Value.TOTP_SECRET, 2)),
        Requirement.TOTP_WINDOW);
    passPasswordStage();
    callAndStopOnFailure(new AnswerTotp(Value.TOTP_SECRET, 1), Requirement.TOTP_WINDOW);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.TOTP_WINDOW);
  }
}
