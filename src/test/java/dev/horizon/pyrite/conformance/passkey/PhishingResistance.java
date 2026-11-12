package dev.horizon.pyrite.conformance.passkey;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPasskeyRequest;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectSessionIssue;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A phishing-resistant stage takes a passkey, and no password.")
final class PhishingResistance extends PasskeyModule {

  @Override
  protected void run() {
    prepareAnne();
    enrolPasskey();
    call(new StartFlow("", "sign-in-with-passkey", EMAIL));
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.INVALID_ARGUMENT, new AnswerPassword(PASSWORD)),
        Requirement.PHISHING_RESISTANCE);
    callAndStopOnFailure(new AnswerPasskeyRequest(), Requirement.PHISHING_RESISTANCE);
    callAndStopOnFailure(new ExpectSessionIssue(), Requirement.PHISHING_RESISTANCE);
  }
}
