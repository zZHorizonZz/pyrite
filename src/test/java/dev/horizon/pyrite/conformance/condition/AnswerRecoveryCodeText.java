package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Answers the last exchange with a recovery code as it is written.
 */
public record AnswerRecoveryCodeText(String code) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(RecoveryCodeAnswer.newBuilder().setCode(code).build());
  }
}
