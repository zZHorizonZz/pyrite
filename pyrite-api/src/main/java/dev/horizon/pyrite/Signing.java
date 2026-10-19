package dev.horizon.pyrite;

import java.time.Duration;
import java.util.Objects;

public record Signing(Algorithm algorithm, Duration ahead, Duration kept) implements Purpose<SigningKey> {

  public Signing {
    Objects.requireNonNull(algorithm, "algorithm");
    Objects.requireNonNull(ahead, "ahead");
    Objects.requireNonNull(kept, "kept");
    if (ahead.isNegative()) {
      throw new IllegalArgumentException("A version is published ahead by no negative time: " + ahead);
    }
    if (kept.isNegative()) {
      throw new IllegalArgumentException("A retired version is kept published for no negative time: " + kept);
    }
  }
}
