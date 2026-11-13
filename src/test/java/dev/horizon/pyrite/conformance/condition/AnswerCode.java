package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Answers the last exchange with the code that Pyrite sent for it.
 */
public record AnswerCode() implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(CodeAnswer.newBuilder().setCode(environment.code(environment.get(Value.EXCHANGE)).getCode())
        .build());
  }
}
