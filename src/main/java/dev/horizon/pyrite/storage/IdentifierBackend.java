package dev.horizon.pyrite.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;

/**
 * The rows of identifiers, in the table {@code pyrite_identifier}, each under its account.
 *
 * @param database the database of Pyrite's tables
 */
public record IdentifierBackend(Database database) implements ResourceBackend<Tables.Identifier> {

  @Override
  public Tables.Identifier prototype() {
    return Tables.Identifier.getDefaultInstance();
  }
}
