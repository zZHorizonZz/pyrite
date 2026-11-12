package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;

/**
 * A password, which the credential holds as its Argon2id hash.
 */
public final class PasswordCredential extends Credential {

  PasswordCredential(CredentialType type, Tables.Factor row) {
    super(type, row);
  }

  /**
   * Tells whether a password is this one, hashing it off the event loop with the parameters of the stored hash.
   *
   * @param password the password, as the person typed it
   * @return whether it matches
   */
  public Future<Boolean> matches(String password) {
    PasswordHash hash = new PasswordHash(row().getSecret().toString(StandardCharsets.UTF_8));
    return type().vertx().executeBlocking(() -> hash.matches(password));
  }

  @Override
  boolean replacesActive() {
    return true;
  }
}
