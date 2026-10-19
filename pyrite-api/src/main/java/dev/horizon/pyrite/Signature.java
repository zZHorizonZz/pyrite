package dev.horizon.pyrite;

import java.util.Objects;

public record Signature(String version, byte[] bytes) {

  public Signature {
    Objects.requireNonNull(version, "version");
    bytes = bytes.clone();
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }
}
