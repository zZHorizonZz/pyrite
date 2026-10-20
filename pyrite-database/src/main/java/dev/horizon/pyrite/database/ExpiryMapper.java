package dev.horizon.pyrite.database;

import dev.horizon.pyrite.database.schema.v1.Tables;
import java.time.Instant;

final class ExpiryMapper implements ProtobufMapper<Instant, Tables.Version, Tables.Version.Builder> {

  @Override
  public Instant read(Tables.Version row) {
    return row.hasExpireTime() ? Instant.parse(row.getExpireTime()) : Instant.MAX;
  }

  @Override
  public Tables.Version.Builder write(Tables.Version.Builder row, Instant expires) {
    return expires.equals(Instant.MAX) ? row.clearExpireTime() : row.setExpireTime(expires.toString());
  }
}
