package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;

public record IdentifierBackend(Database database) implements ResourceBackend<Tables.Identifier> {

  @Override
  public Tables.Identifier prototype() {
    return Tables.Identifier.getDefaultInstance();
  }
}
