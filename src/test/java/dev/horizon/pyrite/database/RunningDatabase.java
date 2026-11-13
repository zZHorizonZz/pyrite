package dev.horizon.pyrite.database;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Driver;
import dev.horizon.cinder.protobuf.ProtobufExtension;
import dev.horizon.cinder.sqlserver.SqlServerDriver;
import dev.horizon.pyrite.storage.AccountBackend;
import dev.horizon.pyrite.storage.DeclarationBackend;
import dev.horizon.pyrite.storage.ExchangeBackend;
import dev.horizon.pyrite.storage.FactorBackend;
import dev.horizon.pyrite.storage.IdentifierBackend;
import dev.horizon.pyrite.storage.KeyBackend;
import dev.horizon.pyrite.storage.KeyVersionBackend;
import dev.horizon.pyrite.storage.NodeBackend;
import dev.horizon.pyrite.storage.SessionBackend;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

public record RunningDatabase(Vertx vertx, Pool pool, Driver driver) implements AutoCloseable {

  public static final Duration TIMEOUT = Duration.ofSeconds(30);

  /**
   * Deletes the rows of Pyrite's tables, children first. The rows of OpenFGA stay: each test writes its declaration
   * into a store of its own, which the rows of another store do not reach.
   */
  public void empty() throws TimeoutException {
    Database rows = Database.builder(pool, driver).extension(new ProtobufExtension()).build();
    NodeBackend nodes = new NodeBackend(rows);
    nodes.execute(nodes.update().set("parent", null).all()).await(TIMEOUT);
    for (Backend<?, ?> backend : List.<Backend<?, ?>> of(new SessionBackend(rows), new FactorBackend(rows),
        new IdentifierBackend(rows), new ExchangeBackend(rows), new AccountBackend(rows), new KeyVersionBackend(rows),
        new KeyBackend(rows), new DeclarationBackend(rows), nodes)) {
      backend.execute(backend.delete().all()).await(TIMEOUT);
    }
  }

  /**
   * Renames a table, so that Pyrite cannot read it. Cinder writes rows, not tables, so this is SQL.
   */
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
