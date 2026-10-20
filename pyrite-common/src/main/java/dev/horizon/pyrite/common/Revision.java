package dev.horizon.pyrite.common;

public record Revision(long value) {

  public Revision {
    if (value < 0) {
      throw new IllegalArgumentException("A revision is not negative: " + value);
    }
  }
}
