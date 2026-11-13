package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Answers the last exchange with a password.
 */
public record AnswerPassword(String password) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(PasswordAnswer.newBuilder().setPassword(password).build());
  }
}
