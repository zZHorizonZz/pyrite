package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.StartExchangeRequest;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;

/**
 * Answers sign-ins with two factors with codes made from the first recovery code of the last enrolment, changed or
 * cut, and checks that each is refused.
 */
public record ExpectRecoveryCodeForgeryRefusal(String email, String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    String code = environment.get(Value.RECOVERY_CODES).getFirst();
    for (String guess : List.of("", code.toUpperCase(), code.substring(1), code + "a", "0000000000")) {
      environment.start(StartExchangeRequest.newBuilder().setDefinition("sign-in-with-two-factors")
          .setIdentifierKind("email").setIdentifier(email).build());
      environment.answer(PasswordAnswer.newBuilder().setPassword(password).build());
      new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerRecoveryCodeText(guess)).evaluate(environment);
    }
  }
}
