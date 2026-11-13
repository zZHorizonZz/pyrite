package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * A password, which the credential holds as its Argon2id hash.
 */
public final class PasswordCredential extends Credential<PasswordAnswer> {

  PasswordCredential(CredentialType type, Tables.Factor row) {
    super(type, row);
  }

  /**
   * Verifies a password, hashing it off the event loop with the parameters of the stored hash.
   *
   * @return {@link Proof#KNOWLEDGE} when the password is this one
   */
  @Override
  public Future<Set<Proof>> verify(PasswordAnswer answer, byte[] nonce) {
    PasswordHash hash = new PasswordHash(row().getSecret().toString(StandardCharsets.UTF_8));
    return type().vertx().executeBlocking(() -> hash.matches(answer.getPassword()))
        .map(right -> right ? Set.of(Proof.KNOWLEDGE) : Set.of());
  }

  @Override
  boolean replacesActive() {
    return true;
  }

  @Override
  boolean signsInAlone() {
    return true;
  }
}
