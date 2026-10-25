package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;

public record SessionBackend(Database database) implements ResourceBackend<Tables.Session> {

  @Override
  public Tables.Session prototype() {
    return Tables.Session.getDefaultInstance();
  }
}
