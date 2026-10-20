package dev.horizon.pyrite.database;

import dev.horizon.basepoint.Filter;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.database.schema.v1.Tables;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

record VersionBackend(Database database) implements Backend<Tables.Version, VersionBackend.Id> {

  @Override
  public Class<Tables.Version> type() {
    return Tables.Version.class;
  }

  Future<List<Tables.Version>> of(String key) {
    return find(Filter.eq("key_name", key)).orderBy("number desc").list();
  }

  Future<Optional<Tables.Version>> latest(String key) {
    return find(Filter.eq("key_name", key)).mask("key_name,number").orderBy("number desc").first();
  }

  record Id(String keyName, long number) {
  }
}
