package dev.horizon.pyrite.conformance.condition;

import com.google.protobuf.Any;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Answers the last flow with an answer as it is packed, such as one that does not parse.
 *
 * @param label what the answer is, for the trace
 */
public record AnswerAny(String label, Any answer) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(answer);
  }

  @Override
  public String toString() {
    return "AnswerAny[" + label + "]";
  }
}
