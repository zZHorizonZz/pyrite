package dev.horizon.pyrite.database;

import dev.horizon.cinder.Driver;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.flywaydb.core.Flyway;

public interface TestDatabase {

  static TestDatabase chosen() {
    String name = System.getProperty("pyrite.test.database", "sqlite");
    return switch (name) {
      case "postgres" -> new PostgresTestDatabase();
      case "mariadb" -> new MariaDbTestDatabase();
      case "sqlserver" -> new SqlServerTestDatabase();
      case "sqlite" -> new SqliteTestDatabase();
      default -> throw new IllegalArgumentException("The property pyrite.test.database is " + name
          + "; it is postgres, mariadb, sqlserver or sqlite.");
    };
  }

  record Location(String url, String user, String password) {
  }

  String name();

  Location start();

  String migrations();

  Driver driver();

  Pool pool(Vertx vertx);

  default RunningDatabase open() {
    Location location = start();
    for (String set : new String[] { "pyrite", "spectrum" }) {
      Flyway.configure().dataSource(location.url(), location.user(), location.password())
          .locations("classpath:" + set + "/migration/**/" + migrations()).table(set + "_schema_history")
          .baselineOnMigrate(true).load().migrate();
    }
    Vertx vertx = Vertx.vertx();
    return new RunningDatabase(vertx, pool(vertx), driver());
  }
}
