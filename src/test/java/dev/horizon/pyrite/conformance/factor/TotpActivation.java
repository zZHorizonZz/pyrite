package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerTotp;
import dev.horizon.pyrite.conformance.condition.AnswerTotpText;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.KeepTotpSecret;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A wrong first code activates nothing.")
final class TotpActivation extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartFlow(ANNE, "enrol-totp", ""));
    call(new KeepTotpSecret(Value.TOTP_SECRET));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerTotpText("000000")),
        Requirement.TOTP_ACTIVATION);
    passPasswordStage();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerTotp(Value.TOTP_SECRET, 0)),
        Requirement.TOTP_ACTIVATION);
  }
}
