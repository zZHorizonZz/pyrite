package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;

public record AccountBackend(Database database) implements ResourceBackend<Tables.Account> {

  @Override
  public Tables.Account prototype() {
    return Tables.Account.getDefaultInstance();
  }
}
