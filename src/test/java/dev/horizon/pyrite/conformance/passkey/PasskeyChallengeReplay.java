package dev.horizon.pyrite.conformance.passkey;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerPasskeyRequest;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.KeepFlow;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An answer to the challenge of another flow is wrong.")
final class PasskeyChallengeReplay extends PasskeyModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolPasskey();
    call(new StartFlow("", "sign-in", EMAIL));
    call(new KeepFlow(Value.OTHER_FLOW));
    call(new StartFlow("", "sign-in", EMAIL));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerPasskeyRequest(Value.OTHER_FLOW)),
        Requirement.PASSKEY_CHALLENGE);
  }
}
