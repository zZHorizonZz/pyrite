package dev.horizon.pyrite.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class MovingClock extends Clock {

  private Instant now;

  MovingClock(Instant now) {
    this.now = now;
  }

  void move(Duration duration) {
    now = now.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now;
  }
}
