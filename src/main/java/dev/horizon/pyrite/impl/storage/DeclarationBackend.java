package dev.horizon.pyrite.impl.storage;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import io.vertx.core.Future;
import java.util.Optional;

public record DeclarationBackend(Database database) implements Backend<Tables.Declaration, String> {

  @Override
  public Tables.Declaration prototype() {
    return Tables.Declaration.getDefaultInstance();
  }

  public Future<Optional<Tables.Declaration>> latest() {
    return findAll().orderBy("create_time desc, id desc").first();
  }
}
