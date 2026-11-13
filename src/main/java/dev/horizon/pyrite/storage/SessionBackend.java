package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.gt;
import static dev.horizon.basepoint.filter.Filter.isNotNull;
import static dev.horizon.basepoint.filter.Filter.isNull;
import static dev.horizon.basepoint.filter.Filter.lt;
import static dev.horizon.basepoint.filter.Filter.lte;
import static dev.horizon.basepoint.filter.Filter.or;

import dev.horizon.basepoint.filter.Filter;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * The rows of sessions. The database's clock decides when a session expires: it writes the expiries, and every read
 * of a live session compares them with its own time.
 */
public record SessionBackend(Database database) implements ResourceBackend<Tables.Session> {

  @Override
  public Tables.Session prototype() {
    return Tables.Session.getDefaultInstance();
  }

  /**
   * Reads the session of a name, if it is live: not ended, and neither of its lifetimes over.
   */
  public Future<Optional<Tables.Session>> findLive(String name) {
    return first(query().where(and(eq("name", name), live())));
  }

  /**
   * Extends the idle lifetime of a session, unless it was extended within an interval.
   */
  public Future<Void> touch(String uid, Duration idleLifetime, Duration interval) {
    return execute(update().set("idle_expire_time", add(now(), idleLifetime)).where(and(eq("uid", uid),
        lt("idle_expire_time", add(now(), idleLifetime.minus(interval)))))).mapEmpty();
  }

  /**
   * Ends a session that has not ended.
   *
   * @return the ended row, or empty when it had ended
   */
  public Future<Optional<Tables.Session>> end(String uid) {
    return returning(update().set("delete_time", now()).set("purge_time", now()).where(and(eq("uid", uid),
        isNull("delete_time")))).map(rows -> rows.stream().findFirst());
  }

  /**
   * Ends the live sessions of an account.
   *
   * @return the ended rows
   */
  public Future<List<Tables.Session>> endLive(String account) {
    return returning(update().set("delete_time", now()).set("purge_time", now()).where(and(eq("parent", account),
        live())));
  }

  /**
   * Deletes the rows of sessions that ended or expired. An ended session goes by its delete time being set, not by
   * comparing its purge time with the clock: a database that keeps a time to fewer digits than its clock gives can
   * store a time just after the clock of the next statement.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return execute(delete().where(or(isNotNull("delete_time"), lte("expire_time", now()))));
  }

  private static Filter live() {
    return and(isNull("delete_time"), gt("expire_time", now()), gt("idle_expire_time", now()));
  }
}
