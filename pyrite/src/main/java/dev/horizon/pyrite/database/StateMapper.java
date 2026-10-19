package dev.horizon.pyrite.database;

import dev.horizon.pyrite.State;
import dev.horizon.pyrite.database.schema.v1.Tables;
import java.time.Instant;

final class StateMapper implements ProtobufMapper<State, Tables.Version, Tables.Version.Builder> {

  private static final String PENDING = "PENDING";
  private static final String PRIMARY = "PRIMARY";
  private static final String LIVE = "LIVE";
  private static final String DISABLED = "DISABLED";
  private static final String DESTROYED = "DESTROYED";

  @Override
  public State read(Tables.Version row) {
    Instant at = row.hasStateTime() ? Instant.parse(row.getStateTime()) : Instant.MAX;
    return switch (row.getState()) {
      case PENDING -> new State.Pending(at);
      case PRIMARY -> new State.Primary(at);
      case LIVE -> new State.Live(at);
      case DISABLED -> new State.Disabled(at);
      case DESTROYED -> new State.Destroyed(at);
      default -> throw new IllegalStateException("The version " + row.getNumber() + " of " + row.getKeyName() + " is "
          + row.getState() + ", which this version of Pyrite does not know");
    };
  }

  @Override
  public Tables.Version.Builder write(Tables.Version.Builder row, State state) {
    return switch (state) {
      case State.Pending pending -> row.setState(PENDING).setStateTime(pending.primaryAt().toString());
      case State.Primary primary -> row.setState(PRIMARY).setStateTime(primary.since().toString());
      case State.Live live -> live.publishedUntil().equals(Instant.MAX)
          ? row.setState(LIVE).clearStateTime()
          : row.setState(LIVE).setStateTime(live.publishedUntil().toString());
      case State.Disabled disabled -> row.setState(DISABLED).setStateTime(disabled.at().toString());
      case State.Destroyed destroyed -> row.setState(DESTROYED).setStateTime(destroyed.at().toString());
    };
  }
}
