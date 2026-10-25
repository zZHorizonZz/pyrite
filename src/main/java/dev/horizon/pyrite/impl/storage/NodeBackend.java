package dev.horizon.pyrite.impl.storage;

import static dev.horizon.basepoint.Filter.eq;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import io.vertx.core.Future;

public record NodeBackend(Database database) implements Backend<Tables.Node, String> {

  @Override
  public Tables.Node prototype() {
    return Tables.Node.getDefaultInstance();
  }

  public Future<Boolean> occupied(String parent) {
    return find(eq("parent", parent)).exists();
  }
}
