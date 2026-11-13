package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Answers the last exchange with a TOTP code as it is written.
 */
public record AnswerTotpText(String code) implements Condition {

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(TotpAnswer.newBuilder().setCode(code).build());
  }
}
