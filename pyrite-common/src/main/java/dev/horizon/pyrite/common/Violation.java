package dev.horizon.pyrite.common;

import java.util.Objects;

public record Violation(String field, String description) {

  public Violation {
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(description, "description");
  }
}
