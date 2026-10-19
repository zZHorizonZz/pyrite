package dev.horizon.pyrite.database;

record VersionName(String key, long number) {

  @Override
  public String toString() {
    return key + "/versions/" + number;
  }
}
