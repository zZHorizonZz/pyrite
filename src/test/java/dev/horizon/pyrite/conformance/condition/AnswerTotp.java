package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.authentication.v1.TotpAnswer;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Answers the last exchange with the TOTP code of a secret, at a step from the current one.
 *
 * @param offset the steps from the current one: -1 for the step before, 0 for the current one
 */
public record AnswerTotp(Value<byte[]> secret, long offset) implements Condition {

  private static final int STEP_SECONDS = 30;
  private static final int BOUND = 1_000_000;

  /**
   * Gives the TOTP code of a secret, at a step from the current one, with SHA-1 and six digits.
   */
  public static String code(byte[] secret, long offset) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(Instant.now().getEpochSecond()
          / STEP_SECONDS + offset).array());
      int at = hash[hash.length - 1] & 0xf;
      int binary = ((hash[at] & 0x7f) << 24) | ((hash[at + 1] & 0xff) << 16) | ((hash[at + 2] & 0xff) << 8)
          | (hash[at + 3] & 0xff);
      return String.format("%06d", binary % BOUND);
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("Every Java platform has HMAC-SHA1", impossible);
    }
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    environment.answer(TotpAnswer.newBuilder().setCode(code(environment.get(secret), offset)).build());
  }
}
