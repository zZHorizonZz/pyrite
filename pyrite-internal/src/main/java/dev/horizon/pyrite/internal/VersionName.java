package dev.horizon.pyrite.internal;

public record VersionName(String key, long number) {

  @Override
  public String toString() {
    return key + "/versions/" + number;
  }
}
