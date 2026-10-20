package dev.horizon.pyrite;

import java.util.Objects;

public record Signing(Algorithm algorithm) implements Purpose<SigningKey> {

  public Signing {
    Objects.requireNonNull(algorithm, "algorithm");
  }
}
