package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectNoDelivery;
import dev.horizon.pyrite.conformance.condition.ExpectSameChallenges;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.KeepExchange;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An unknown address takes the same answers, none of them right, and exhausts the same way.")
final class UnknownAddress extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange("", "sign-in", EMAIL));
    call(new KeepExchange(Value.OTHER_EXCHANGE));
    call(new StartExchange("", "sign-in", "nobody@acme.dev"));
    callAndContinueOnFailure(new ExpectSameChallenges(Value.OTHER_EXCHANGE), Requirement.PASSWORD_ENUMERATION);
    callAndContinueOnFailure(new ExpectNoDelivery(), Requirement.PASSWORD_ENUMERATION);
    for (int attempt = 0; attempt < 3; attempt++) {
      callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerPassword(PASSWORD)),
          Requirement.PASSWORD_ENUMERATION);
    }
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.RESOURCE_EXHAUSTED, new AnswerPassword(PASSWORD)),
        Requirement.PASSWORD_ENUMERATION);
  }
}
