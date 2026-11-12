package dev.horizon.pyrite.conformance.factor;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerTotp;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A code is taken once, and an older code after it is not.")
final class TotpReplay extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolTotp();
    passPasswordStage();
    call(new AnswerTotp(Value.TOTP_SECRET, 0));
    passPasswordStage();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerTotp(Value.TOTP_SECRET, 0)),
        Requirement.TOTP_REPLAY);
    passPasswordStage();
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerTotp(Value.TOTP_SECRET, -1)),
        Requirement.TOTP_REPLAY);
  }
}
