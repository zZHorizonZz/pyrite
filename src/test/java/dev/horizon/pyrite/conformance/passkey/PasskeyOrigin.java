package dev.horizon.pyrite.conformance.passkey;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPasskeyCreation;
import dev.horizon.pyrite.conformance.condition.CreatePasskeyClient;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An answer from an origin that the relying party does not name is wrong.")
final class PasskeyOrigin extends PasskeyModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreatePasskeyClient("https://acme.example"));
    call(new StartFlow(ANNE, "enrol-passkey", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerPasskeyCreation()),
        Requirement.PASSKEY_ORIGIN);
  }
}
