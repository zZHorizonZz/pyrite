package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * A passkey: a WebAuthn credential, with its id, its signature counter, and whether its authenticator verifies the
 * user. An account has as many passkeys as it enrols.
 */
public final class PasskeyCredential extends Credential {

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
   * Verifies a WebAuthn assertion of the passkey against the challenge of a flow, and keeps its new counter.
   *
   * @param assertion the JSON of the credential that {@code navigator.credentials.get} gave
   * @param challenge the challenge of the flow, which the request options held
   * @return the assertion, with whether the authenticator verified the user, or nothing if it does not verify
   */
  public Future<Optional<WebAuthn.Assertion>> accepts(String assertion, byte[] challenge) {
    Optional<WebAuthn.Assertion> verdict = type().webAuthn().verify(assertion, challenge,
        row().getSecret().toByteArray(), credentialId(), row().getCounter(), row().getUserVerified());
    return verdict.isEmpty() ? Future.succeededFuture(verdict)
        : change(row().toBuilder().setCounter(verdict.get().counter())).map(verdict);
  }

  @Override
  boolean replacesActive() {
    return false;
  }
}
