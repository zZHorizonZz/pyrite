package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * A TOTP secret, which the credential holds encrypted by the encryption key of the options, with its name as the
 * associated data. Its counter is the last time step that a code matched, so a code works once.
 */
public final class TotpCredential extends Credential {

  TotpCredential(CredentialType type, Tables.Factor row) {
    super(type, row);
  }

  /**
   * Tells whether a code is that of the current time step, or of the step before or after it, and of a step after the
   * last one that matched. A code that matches becomes the last one, so it works once. The time step comes from the
   * system time of Java, not from the clock of the database.
   *
   * @param code the code of six digits
   * @return whether the code matches
   */
  public Future<Boolean> accepts(String code) {
    return secret().compose(secret -> {
      OptionalLong step = secret.match(code, Instant.now(), row().getCounter());
      return step.isEmpty() ? Future.succeededFuture(false)
          : change(row().toBuilder().setCounter(step.getAsLong())).map(true);
    });
  }

  /**
   * Gives the otpauth URI of the secret, which an authenticator app reads from a QR code.
   *
   * @param label the label that the app shows, such as the name of the account
   * @return the URI, with the issuer of the options, SHA-1, six digits and a period of 30 seconds
   */
  public Future<String> uri(String label) {
    return secret().map(secret -> secret.uri(type().issuer(), label));
  }

  @Override
  boolean replacesActive() {
    return true;
  }

  private Future<TotpSecret> secret() {
    return type().encryptionKey()
        .compose(key -> key.decrypt(row().getSecret().toByteArray(), name().getBytes(StandardCharsets.UTF_8)))
        .map(plain -> new TotpSecret(plain.getPlaintext().toByteArray()));
  }
}
