package dev.horizon.pyrite.identity;

import com.google.protobuf.Message;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Set;

/**
 * A credential of an account, a factor under it: a password, a TOTP secret, a passkey, a set of recovery codes, or a
 * linked account at a provider. Each
 * kind verifies the answer that proves it, and tells what a right answer proves. An exchange enrols a credential as
 * pending, and activates it when the exchange ends; a pending credential that no exchange activates is purged after the
 * lifetime of exchanges.
 *
 * @param <A> the message of the answer that proves the credential, such as {@code PasswordAnswer}
 */
public abstract sealed class Credential<A extends Message>
    permits PasswordCredential, TotpCredential, PasskeyCredential, RecoveryCodeCredential, ProviderCredential {

  private final CredentialType type;
  private final Tables.Factor row;

  Credential(CredentialType type, Tables.Factor row) {
    this.type = type;
    this.row = row;
  }

  public String name() {
    return row.getName();
  }

  public String account() {
    return row.getParent();
  }

  /**
   * Tells whether the credential counts in a sign-in: an exchange activated it, and it is not removed.
   *
   * @return whether it is active
   */
  public boolean isActive() {
    return row.getState() == Tables.Factor.State.ACTIVE && !row.hasDeleteTime();
  }

  /**
   * Activates the credential, after removing the active credentials of its kind that it replaces: a password, a TOTP
   * secret or a set of recovery codes replaces the one before, and a passkey replaces none.
   *
   * @return the active credential, or a failure with {@code ABORTED} when another change came first
   */
  public Future<Credential<A>> activate() {
    if (!replacesActive()) {
      return change(row.toBuilder().setState(Tables.Factor.State.ACTIVE).clearPurgeTime());
    }
    return type.credentials(account()).compose(credentials -> {
      Future<Void> removing = Future.succeededFuture();
      for (Credential<?> credential : credentials) {
        if (credential.row.getKind() == row.getKind() && credential.isActive()) {
          removing = removing.compose(ignored -> credential.remove());
        }
      }
      return removing;
    }).compose(ignored -> change(row.toBuilder().setState(Tables.Factor.State.ACTIVE).clearPurgeTime()));
  }

  /**
   * Removes the credential; its row stays until a purge.
   *
   * @return a future that completes once it is removed, or a failure with {@code ABORTED} when another change came
   * first
   */
  public Future<Void> remove() {
    return type.remove(this).mapEmpty();
  }

  /**
   * Gives the credential as the result of an exchange names it, without its secret.
   *
   * @return the factor, with its name, its account and its kind
   */
  public dev.horizon.pyrite.authentication.v1.Factor message() {
    return dev.horizon.pyrite.authentication.v1.Factor.newBuilder().setName(name()).setAccount(account())
        .setKind(dev.horizon.pyrite.authentication.v1.Factor.Kind.valueOf(row.getKind().name()))
        .setProvider(row.getKind() == Tables.Factor.Kind.PROVIDER ? row.getCredentialId().substring(0, row
            .getCredentialId().indexOf(':')) : "")
        .build();
  }

  /**
   * Tells whether an account has one active credential of this kind, which a new one replaces when it is activated.
   */
  /**
   * Verifies an answer. A credential that is used up by a right answer, such as a recovery code or a TOTP time step,
   * keeps that it was used.
   *
   * @param answer the answer, as the person gave it
   * @param nonce the nonce of the stage of the exchange, which a passkey answer signs
   * @return what the answer proves, or nothing for a wrong answer
   */
  public abstract Future<Set<Proof>> verify(A answer, byte[] nonce);

  abstract boolean replacesActive();

  /**
   * Tells whether the credential signs in on its own, as a password, a passkey or a linked provider account do; a TOTP
   * secret and recovery codes are second factors.
   */
  abstract boolean signsInAlone();

  CredentialType type() {
    return type;
  }

  Tables.Factor row() {
    return row;
  }

  @SuppressWarnings("unchecked")
  Future<Credential<A>> change(Tables.Factor.Builder changed) {
    return type.save(this, changed).map(saved -> (Credential<A>) saved);
  }
}
