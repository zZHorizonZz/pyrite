package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;

/**
 * An identifier of an account, under it: a value of a declared identifier kind, such as an email address, in the form
 * that the kind normalizes, and unique among the identifiers of its kind. It is unverified until an exchange proves it
 * with a code.
 */
public final class Identifier {

  private final IdentifierType type;
  private final Tables.Identifier row;

  Identifier(IdentifierType type, Tables.Identifier row) {
    this.type = type;
    this.row = row;
  }

  public String name() {
    return row.getName();
  }

  public String account() {
    return row.getParent();
  }

  public String kind() {
    return row.getKind();
  }

  public String value() {
    return row.getValue();
  }

  public boolean isVerified() {
    return row.getVerified();
  }

  /**
   * Tells whether a code can go to the identifier: it is not removed, and its kind receives codes.
   */
  public boolean isDeliverable() {
    return !row.hasDeleteTime() && type.receivesCodes(kind());
  }

  /**
   * Marks the identifier as verified.
   *
   * @return the verified identifier, or a failure with {@code ABORTED} when another change came first
   */
  public Future<Identifier> verify() {
    return type.save(this, row.toBuilder().setVerified(true));
  }

  /**
   * Gives the identifier as the authentication service gives it out.
   *
   * @return the message, with the name, the account, the kind, the value, and whether it is verified and primary
   */
  public dev.horizon.pyrite.authentication.v1.Identifier message() {
    return dev.horizon.pyrite.authentication.v1.Identifier.newBuilder().setName(name()).setAccount(account())
        .setKind(kind()).setValue(value()).setVerified(isVerified()).setPrimary(row.getPrimary()).build();
  }

  Tables.Identifier row() {
    return row;
  }
}
