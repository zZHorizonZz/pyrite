package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Set;

@Summary("Any other password is wrong.")
final class WrongPassword extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    for (String guess : List.of("Correct horse battery staple", "correct horse battery staple ", "correct horse",
        "correct horse battery stapl", "", "CORRECT HORSE BATTERY STAPLE")) {
      call(new StartExchange("", "sign-in", EMAIL));
      callAndContinueOnFailure(new ExpectStatus(Set.of(GrpcStatus.PERMISSION_DENIED, GrpcStatus.INVALID_ARGUMENT),
          new AnswerPassword(guess)), Requirement.PASSWORD_SIGN_IN);
    }
  }
}
