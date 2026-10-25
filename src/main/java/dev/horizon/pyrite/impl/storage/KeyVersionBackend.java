package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.keys.v1.Tables;

public record KeyVersionBackend(Database database) implements ResourceBackend<Tables.KeyVersion> {

  @Override
  public Tables.KeyVersion prototype() {
    return Tables.KeyVersion.getDefaultInstance();
  }
}
