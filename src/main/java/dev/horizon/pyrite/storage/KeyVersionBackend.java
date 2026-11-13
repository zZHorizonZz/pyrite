package dev.horizon.pyrite.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.keys.v1.Tables;

/**
 * The rows of key versions, in the table {@code pyrite_key_version}, each under its key, with its material sealed by
 * the root key.
 *
 * @param database the database of Pyrite's tables
 */
public record KeyVersionBackend(Database database) implements ResourceBackend<Tables.KeyVersion> {

  @Override
  public Tables.KeyVersion prototype() {
    return Tables.KeyVersion.getDefaultInstance();
  }
}
