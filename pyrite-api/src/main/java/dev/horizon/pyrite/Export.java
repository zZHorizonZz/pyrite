package dev.horizon.pyrite;

import java.util.Objects;

public record Export<V>(V version, byte[] privateKey) {

  public Export {
    Objects.requireNonNull(version, "version");
    privateKey = privateKey.clone();
  }

  @Override
  public byte[] privateKey() {
    return privateKey.clone();
  }
}
