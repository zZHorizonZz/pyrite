package dev.horizon.pyrite.database;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.database.schema.v1.Tables;

record VaultTable(Database database) implements Backend<Tables.Vault, String> {

  @Override
  public Class<Tables.Vault> type() {
    return Tables.Vault.class;
  }
}
