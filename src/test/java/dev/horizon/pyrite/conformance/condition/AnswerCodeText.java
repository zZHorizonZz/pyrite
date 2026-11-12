package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Answers the last flow with a code as it is written.
 */
public record AnswerCodeText(String code) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(CodeAnswer.newBuilder().setCode(code).build());
  }
}
