package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AddIdentifier;
import dev.horizon.pyrite.conformance.condition.AnswerCodeText;
import dev.horizon.pyrite.conformance.condition.ExpectNoDelivery;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartFlow;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An address that is not verified gets no recovery code.")
final class RecoveryWithoutVerification extends RecoveryModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new AddIdentifier(ANNE, "accounts/anne", "email", "anne@home.example"));
    call(new StartFlow("", "recover", "anne@home.example"));
    callAndContinueOnFailure(new ExpectNoDelivery(), Requirement.RECOVERY_ADDRESS);
    callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerCodeText("000000")),
        Requirement.RECOVERY_ADDRESS);
  }
}
