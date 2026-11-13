package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.authentication.v1.PasskeyAnswer;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Optional;
import java.util.Set;

/**
 * A passkey: a WebAuthn credential, with its id, its signature counter, and whether its authenticator verifies the
 * user. An account has as many passkeys as it enrols.
 */
public final class PasskeyCredential extends Credential<PasskeyAnswer> {

  PasskeyCredential(CredentialType type, Tables.Factor row) {
    super(type, row);
  }

  /**
   * Gives the id that the authenticator gave the passkey, which an assertion names.
   *
   * @return the id, in base64url without padding
   */
  public String credentialId() {
    return row().getCredentialId();
  }

  /**
   * Verifies a WebAuthn assertion of this passkey, the JSON of the credential that {@code navigator.credentials.get}
   * gave, against the nonce of the stage, which the request options held as their challenge. A right assertion keeps
   * its new signature counter.
   *
   * @return {@link Proof#POSSESSION}, and {@link Proof#INHERENCE} too when the authenticator verified the user; nothing
   * for an assertion of another passkey, or one that does not verify
   */
  @Override
  public Future<Set<Proof>> verify(PasskeyAnswer answer, byte[] nonce) {
    String assertion = answer.getCredential();
    if (!type().webAuthn().credential(assertion).filter(credentialId()::equals).isPresent()) {
      return Future.succeededFuture(Set.of());
    }
    Optional<WebAuthn.Assertion> verdict = type().webAuthn().verify(assertion, nonce, row().getSecret().toByteArray(),
        credentialId(), row().getCounter(), row().getUserVerified());
    if (verdict.isEmpty()) {
      return Future.succeededFuture(Set.of());
    }
    return change(row().toBuilder().setCounter(verdict.get().counter())).map(saved -> verdict.get().userVerified()
        ? Set.of(Proof.POSSESSION, Proof.INHERENCE)
        : Set.of(Proof.POSSESSION));
  }

  @Override
  boolean replacesActive() {
    return false;
  }

  @Override
  boolean signsInAlone() {
    return true;
  }
}
