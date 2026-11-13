package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.filter.Filter.eq;

import dev.horizon.basepoint.Direction;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import io.vertx.core.Future;
import java.util.Optional;

/**
 * The rows of declarations, in the table {@code pyrite_declaration}, each with the store of grants and the
 * authorization model that it wrote. The database refuses a row on a declaration that another row is written on
 * already, with {@link dev.horizon.cinder.DuplicateKeyException}.
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

  /**
   * Reads the declaration that was written on another.
   *
   * @param previous the id of the other declaration; empty for none, to read the first
   * @return the declaration whose {@code previous_id} is the id, or empty when none is written on it
   */
  public Future<Optional<Tables.Declaration>> next(String previous) {
    return first(query().where(eq("previous_id", previous)));
  }
}
