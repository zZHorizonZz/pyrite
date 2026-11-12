package dev.horizon.pyrite.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;

/**
 * The rows of accounts, in the table {@code pyrite_account}. The database deletes the row of an account with its
 * node, and the rows of its identifiers, factors, sessions and flows with it.
 *
 * @param database the database of Pyrite's tables
 */
public record AccountBackend(Database database) implements ResourceBackend<Tables.Account> {

  @Override
  public Tables.Account prototype() {
    return Tables.Account.getDefaultInstance();
  }
}
