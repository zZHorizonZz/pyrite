package dev.horizon.pyrite.database;

import dev.horizon.cinder.protobuf.ProtobufMapper;
import dev.horizon.pyrite.Validity;
import dev.horizon.pyrite.database.schema.v1.Tables;
import java.time.Duration;

final class ValidityMapper implements ProtobufMapper<Validity, Tables.Key, Tables.Key.Builder> {

  @Override
  public Validity read(Tables.Key row) {
    Duration pending = Duration.ofMillis(row.getPendingMillis());
    return row.hasLifetimeMillis()
        ? Validity.of(Duration.ofMillis(row.getLifetimeMillis())).pending(pending)
        : Validity.unlimited().pending(pending);
  }

  @Override
  public Tables.Key.Builder write(Tables.Key.Builder row, Validity validity) {
    row.setPendingMillis(validity.pending().toMillis());
    return switch (validity) {
      case Validity.Limited limited -> row.setLifetimeMillis(limited.lifetime().toMillis());
      case Validity.Unlimited unlimited -> row.clearLifetimeMillis();
    };
  }
}
