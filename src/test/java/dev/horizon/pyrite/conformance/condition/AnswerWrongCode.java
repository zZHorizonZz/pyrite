package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Answers the last exchange with a code next to the one that Pyrite sent for it.
 */
public record AnswerWrongCode() implements Condition {

  private static final int BOUND = 1_000_000;

  @Override
  public void evaluate(Environment environment) throws Exception {
    int sent = Integer.parseInt(environment.code(environment.get(Value.EXCHANGE)).getCode());
    environment.answer(CodeAnswer.newBuilder().setCode(String.format("%06d", (sent + 1) % BOUND)).build());
  }
}
