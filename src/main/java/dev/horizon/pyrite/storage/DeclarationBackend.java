package dev.horizon.pyrite.storage;

import dev.horizon.basepoint.Direction;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The rows of declarations, in the table {@code pyrite_declaration}, each with the OpenFGA store and the authorization
 * model that it wrote.
 *
 * @param database the database of Pyrite's tables
 */
public record DeclarationBackend(Database database) implements Backend<Tables.Declaration, String> {

  @Override
  public Tables.Declaration prototype() {
    return Tables.Declaration.getDefaultInstance();
  }

  /**
   * Reads the latest declaration: the one that the database stored last, or of two stored at the same time, the one
   * of the greater id.
   *
   * @return the declaration, or empty before the first
   */
  public Future<Optional<Tables.Declaration>> latest() {
    return first(query().orderBy("create_time", Direction.DESC).orderBy("id", Direction.DESC));
  }
}
