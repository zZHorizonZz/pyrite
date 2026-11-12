package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.RecoveryCodeAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Answers the last flow with a recovery code of an enrolment.
 */
public record AnswerRecoveryCode(Value<List<String>> codes, int index) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(RecoveryCodeAnswer.newBuilder().setCode(environment.get(codes).get(index)).build());
  }
}
