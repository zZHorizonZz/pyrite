package dev.horizon.pyrite.identity;

import com.google.common.io.BaseEncoding;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A TOTP secret of RFC 6238, of 20 random bytes when {@link #generate()} makes it, whose codes are six digits of an
 * HMAC-SHA1 of a time step of 30 seconds.
 *
 * @param bytes the secret
 */
record TotpSecret(byte[] bytes) {

  private static final int BYTES = 20;
  private static final long PERIOD = 30;
  private static final int DIGITS = 6;
  private static final int MODULUS = 1_000_000;
  private static final SecureRandom RANDOM = new SecureRandom();

  public TotpSecret {
    bytes = bytes.clone();
  }

  /**
   * Makes a random secret of 160 bits.
   *
   * @return the secret
   */
  public static TotpSecret generate() {
    byte[] bytes = new byte[BYTES];
    RANDOM.nextBytes(bytes);
    return new TotpSecret(bytes);
  }

  /**
   * Gives the step of a time: the number of 30-second periods since the epoch.
   *
   * @param time the time, by the clock of the server
   * @return the step
   */
  public static long step(Instant time) {
    return time.getEpochSecond() / PERIOD;
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  /**
   * Gives the code of a step, by RFC 6238 with HMAC-SHA1 and six digits.
   *
   * @param step the step
   * @return the code, such as {@code 042137}
   */
  public String code(long step) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(bytes, "HmacSHA1"));
      byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
      int offset = hash[hash.length - 1] & 0xf;
      int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
          | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
      return String.format("%0" + DIGITS + "d", binary % MODULUS);
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("HmacSHA1 is a required algorithm of every Java platform", impossible);
    }
  }

  /**
   * Finds the step of a code: the step of a time, or the one before or after it, if it comes after the last step that
   * matched.
   *
   * @param code the code
   * @param now the time
   * @param last the last step that matched, or {@code -1} for none
   * @return the step, or nothing if the code is of none of them
   */
  public OptionalLong match(String code, Instant now, long last) {
    long current = step(now);
    for (long step = current - 1; step <= current + 1; step++) {
      if (step > last && MessageDigest.isEqual(code(step).getBytes(StandardCharsets.US_ASCII),
          code.getBytes(StandardCharsets.US_ASCII))) {
        return OptionalLong.of(step);
      }
    }
    return OptionalLong.empty();
  }

  /**
   * Gives the {@code otpauth://} URI that an authenticator app reads from a QR code.
   *
   * @param issuer the issuer that the app shows, such as {@code Acme}
   * @param label the label of the account in the app, such as {@code accounts/anne}
   * @return the URI, with the secret in Base32
   */
  public String uri(String issuer, String label) {
    String encodedIssuer = URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20");
    return "otpauth://totp/" + encodedIssuer + ":" + URLEncoder.encode(label, StandardCharsets.UTF_8)
        .replace("+", "%20") + "?secret=" + BaseEncoding.base32().omitPadding().encode(bytes) + "&issuer="
        + encodedIssuer + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD;
  }
}
