package dev.horizon.pyrite;

import java.time.Duration;
import java.util.Objects;

public record Expected(String type, String issuer, String audience, Duration leeway) {

  public Expected {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(audience, "audience");
    Objects.requireNonNull(leeway, "leeway");
    if (leeway.isNegative()) {
      throw new IllegalArgumentException("A leeway is not negative: " + leeway);
    }
  }

  public Expected(String type, String issuer, String audience) {
    this(type, issuer, audience, Duration.ZERO);
  }
}
