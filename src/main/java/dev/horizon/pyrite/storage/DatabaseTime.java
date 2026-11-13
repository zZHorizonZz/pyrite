package dev.horizon.pyrite.storage;

import com.google.protobuf.Timestamp;
import java.time.Instant;

/**
 * A time as the database writes it: ISO-8601 text in UTC, such as {@code 2026-11-12T09:30:00.123456Z}, which the
 * messages of Pyrite give as a {@link Timestamp}.
 */
public final class DatabaseTime {

  private DatabaseTime() {
  }

  /**
   * Reads a time that the database wrote.
   *
   * @param text the time, such as {@code 2026-11-12T09:30:00.123456Z}
   * @return the instant
   * @throws java.time.format.DateTimeParseException if the text is not an ISO-8601 instant
   */
  public static Instant instant(String text) {
    return Instant.parse(text);
  }

  /**
   * Reads a time that the database wrote, for a message.
   *
   * @param text the time, such as {@code 2026-11-12T09:30:00.123456Z}
   * @return the timestamp, to the nanosecond of the text
   * @throws java.time.format.DateTimeParseException if the text is not an ISO-8601 instant
   */
  public static Timestamp timestamp(String text) {
    Instant instant = instant(text);
    return Timestamp.newBuilder().setSeconds(instant.getEpochSecond()).setNanos(instant.getNano()).build();
  }
}
