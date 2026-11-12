package dev.horizon.pyrite.conformance.factor;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerForeignTotp;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A code of another secret is wrong.")
final class TotpOfOtherSecret extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolTotp();
    passPasswordStage();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerForeignTotp()), Requirement.TOTP_WRONG);
  }
}
