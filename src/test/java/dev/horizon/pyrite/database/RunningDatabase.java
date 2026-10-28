package dev.horizon.pyrite.database;

import dev.horizon.cinder.Driver;
import dev.horizon.cinder.sqlserver.SqlServerDriver;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

public record RunningDatabase(Vertx vertx, Pool pool, Driver driver) implements AutoCloseable {

  public static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final List<String> TABLES = List.of("pyrite_node", "pyrite_session", "pyrite_factor", "pyrite_identifier",
      "pyrite_account", "pyrite_flow",
      "pyrite_key_version", "pyrite_key", "pyrite_declaration", "spectrum_tuple", "spectrum_changelog",
      "spectrum_assertion", "spectrum_authorization_model", "spectrum_store");

  public void empty() throws TimeoutException {
    for (String table : TABLES) {
      pool.query("DELETE FROM " + table).execute().await(TIMEOUT);
    }
  }

  public void rename(String table, String to) throws TimeoutException {
    pool.query(driver instanceof SqlServerDriver
        ? "EXEC sp_rename '" + table + "', '" + to + "'"
        : "ALTER TABLE " + table + " RENAME TO " + to).execute().await(TIMEOUT);
  }

  @Override
  public void close() throws TimeoutException {
    pool.close().await(TIMEOUT);
    vertx.close().await(TIMEOUT);
  }
}
