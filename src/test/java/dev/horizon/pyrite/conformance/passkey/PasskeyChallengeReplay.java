package dev.horizon.pyrite.conformance.passkey;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerPasskeyRequest;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.KeepExchange;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An answer to the challenge of another exchange is wrong.")
final class PasskeyChallengeReplay extends PasskeyModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolPasskey();
    call(new StartExchange("", "sign-in", EMAIL));
    call(new KeepExchange(Value.OTHER_EXCHANGE));
    call(new StartExchange("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerPasskeyRequest(Value.OTHER_EXCHANGE)),
        Requirement.PASSKEY_CHALLENGE);
  }
}
