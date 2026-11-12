package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.lte;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.Map;

/**
 * The rows of credentials. The database's clock decides when a removed credential, or one that was enrolled and not
 * activated, is purged.
 */
public record FactorBackend(Database database) implements ResourceBackend<Tables.Factor> {

  @Override
  public Tables.Factor prototype() {
    return Tables.Factor.getDefaultInstance();
  }

  /**
   * Removes a credential, if it is still at a revision; its row stays until a purge.
   */
  public Future<Tables.Factor> remove(Tables.Factor factor) {
    return update(factor, Map.of("delete_time", now(), "purge_time", now()), factor.getUid(), factor.getRevision(),
        factor.getName());
  }

  /**
   * Deletes the rows of credentials whose purge time passed.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return execute(delete().where(lte("purge_time", now())));
  }
}
