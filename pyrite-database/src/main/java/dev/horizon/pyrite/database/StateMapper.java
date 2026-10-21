package dev.horizon.pyrite.database;

import dev.horizon.cinder.protobuf.ProtobufMapper;
import dev.horizon.pyrite.State;
import dev.horizon.pyrite.database.schema.v1.Tables;
import java.time.Instant;

final class StateMapper implements ProtobufMapper<State, Tables.Version, Tables.Version.Builder> {

  private static final String PENDING = "PENDING";
  private static final String PRIMARY = "PRIMARY";
  private static final String LIVE = "LIVE";
  private static final String DISABLED = "DISABLED";
  private static final String EXPIRED = "EXPIRED";
  private static final String DESTROYED = "DESTROYED";

  @Override
  public State read(Tables.Version row) {
    return switch (row.getState()) {
      case PENDING -> new State.Pending(at(row));
      case PRIMARY -> new State.Primary(at(row));
      case LIVE -> new State.Live();
      case DISABLED -> new State.Disabled(at(row));
      case EXPIRED -> new State.Expired(at(row));
      case DESTROYED -> new State.Destroyed(at(row));
      default -> throw new IllegalStateException("The version " + row.getNumber() + " of " + row.getKeyName() + " is "
          + row.getState() + ", which this version of Pyrite does not know");
    };
  }

  @Override
  public Tables.Version.Builder write(Tables.Version.Builder row, State state) {
    return switch (state) {
      case State.Pending pending -> row.setState(PENDING).setStateTime(pending.primaryAt().toString());
      case State.Primary primary -> row.setState(PRIMARY).setStateTime(primary.since().toString());
      case State.Live live -> row.setState(LIVE).clearStateTime();
      case State.Disabled disabled -> row.setState(DISABLED).setStateTime(disabled.at().toString());
      case State.Expired expired -> row.setState(EXPIRED).setStateTime(expired.at().toString());
      case State.Destroyed destroyed -> row.setState(DESTROYED).setStateTime(destroyed.at().toString());
    };
  }

  private Instant at(Tables.Version row) {
    if (!row.hasStateTime()) {
      throw new IllegalStateException("The version " + row.getNumber() + " of " + row.getKeyName() + " is "
          + row.getState() + " and has no state time");
    }
    return Instant.parse(row.getStateTime());
  }
}
