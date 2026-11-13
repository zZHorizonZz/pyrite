package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.expression.Expression.now;
import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.basepoint.filter.Filter.isNull;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;

/**
 * The rows of refresh tokens. A token is used once: the database takes it for one refresh only, so two refreshes with
 * the same token cannot both succeed.
 *
 * @param database the database of Pyrite's tables
 */
public record RefreshTokenBackend(Database database) implements Backend<Tables.RefreshToken, String> {

  @Override
  public Tables.RefreshToken prototype() {
    return Tables.RefreshToken.getDefaultInstance();
  }

  /**
   * Takes a token for a refresh, if no refresh took it before.
   *
   * @param id the id of the token
   * @return whether this refresh took it; false when an earlier refresh did
   */
  public Future<Boolean> use(String id) {
    return execute(update().set("use_time", now()).where(and(eq("id", id), isNull("use_time"))))
        .map(count -> count > 0);
  }
}
