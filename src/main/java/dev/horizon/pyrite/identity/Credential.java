package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;

/**
 * A credential of an account, a factor under it: a password, a TOTP secret, a passkey or a set of recovery codes. A
 * flow enrols it as pending, and activates it when the flow ends; a pending credential that no flow activates is
 * purged after the lifetime of flows.
 */
public abstract sealed class Credential
    permits PasswordCredential, TotpCredential, PasskeyCredential, RecoveryCodeCredential {

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
   * Tells whether the credential counts in a sign-in: a flow activated it, and it is not removed.
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
  public Future<Credential> activate() {
    if (!replacesActive()) {
      return change(row.toBuilder().setState(Tables.Factor.State.ACTIVE).clearPurgeTime());
    }
    return type.credentials(account()).compose(credentials -> {
      Future<Void> removing = Future.succeededFuture();
      for (Credential credential : credentials) {
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
   * Gives the credential as the result of a flow names it, without its secret.
   *
   * @return the factor, with its name, its account and its kind
   */
  public dev.horizon.pyrite.authentication.v1.Factor message() {
    return dev.horizon.pyrite.authentication.v1.Factor.newBuilder().setName(name()).setAccount(account())
        .setKind(dev.horizon.pyrite.authentication.v1.Factor.Kind.valueOf(row.getKind().name())).build();
  }

  /**
   * Tells whether an account has one active credential of this kind, which a new one replaces when it is activated.
   */
  abstract boolean replacesActive();

  CredentialType type() {
    return type;
  }

  Tables.Factor row() {
    return row;
  }

  Future<Credential> change(Tables.Factor.Builder changed) {
    return type.save(this, changed);
  }
}
