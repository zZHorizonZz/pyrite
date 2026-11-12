package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;

/**
 * Answers the last flow with the current TOTP code of a secret that no account has.
 */
public record AnswerForeignTotp() implements Condition {

  private static final int SECRET_BYTES = 20;

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(TotpAnswer.newBuilder().setCode(AnswerTotp.code(new byte[SECRET_BYTES], 0)).build());
  }
}
