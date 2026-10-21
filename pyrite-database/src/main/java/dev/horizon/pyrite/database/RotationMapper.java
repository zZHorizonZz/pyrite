package dev.horizon.pyrite.database;

import dev.horizon.cinder.protobuf.ProtobufMapper;
import dev.horizon.pyrite.Rotation;
import dev.horizon.pyrite.database.schema.v1.Tables;
import java.time.Duration;

final class RotationMapper implements ProtobufMapper<Rotation, Tables.Key, Tables.Key.Builder> {

  @Override
  public Rotation read(Tables.Key row) {
    return row.getRotationMillis() == 0
        ? Rotation.never()
        : Rotation.every(Duration.ofMillis(row.getRotationMillis()));
  }

  @Override
  public Tables.Key.Builder write(Tables.Key.Builder row, Rotation rotation) {
    return row.setRotationMillis(switch (rotation) {
      case Rotation.Every every -> every.period().toMillis();
      case Rotation.Never never -> 0L;
    });
  }
}
