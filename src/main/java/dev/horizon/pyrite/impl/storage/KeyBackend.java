package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.keys.v1.Tables;

public record KeyBackend(Database database) implements ResourceBackend<Tables.Key> {

  @Override
  public Tables.Key prototype() {
    return Tables.Key.getDefaultInstance();
  }
}
