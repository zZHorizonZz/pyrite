package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.isNull;
import static dev.horizon.basepoint.filter.Filter.lte;

import dev.horizon.basepoint.LockMode;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.util.List;
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
   * Reads the factors of an account that are not removed, and locks them until the transaction ends, so that two
   * removals of factors of one account run one after the other.
   *
   * @param account the name of the account
   * @return the factors
   */
  public Future<List<Tables.Factor>> lockLive(String account) {
    return list(query().where(and(eq("parent", account), isNull("delete_time"))).lock(LockMode.UPDATE));
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
