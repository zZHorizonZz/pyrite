package dev.horizon.pyrite.database;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.database.schema.v1.Tables;

record KeyTable(Database database) implements Backend<Tables.Key, String> {

  @Override
  public Class<Tables.Key> type() {
    return Tables.Key.class;
  }
}
