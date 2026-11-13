package dev.horizon.pyrite.conformance.code;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerCodeText;
import dev.horizon.pyrite.conformance.condition.ExpectNoDelivery;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

@Summary("A recovery of an unknown address sends nothing and takes no code.")
final class UnknownRecoveryAddress extends RecoveryModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new StartExchange("", "recover", "nobody@acme.dev"));
    callAndContinueOnFailure(new ExpectNoDelivery(), Requirement.RECOVERY_ADDRESS);
    for (String guess : List.of("000000", "123456", "999999")) {
      callAndContinueOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerCodeText(guess)),
          Requirement.RECOVERY_ADDRESS);
    }
  }
}
