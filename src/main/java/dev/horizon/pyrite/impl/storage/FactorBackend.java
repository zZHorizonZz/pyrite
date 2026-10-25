package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;

public record FactorBackend(Database database) implements ResourceBackend<Tables.Factor> {

  @Override
  public Tables.Factor prototype() {
    return Tables.Factor.getDefaultInstance();
  }
}
