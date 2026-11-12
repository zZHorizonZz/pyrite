package dev.horizon.pyrite.database;

import dev.horizon.cinder.Driver;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import java.util.List;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;

public interface TestDatabase {

  String PROPERTY = "pyrite.test.database";

  static TestDatabase chosen() {
    return named(System.getProperty(PROPERTY, "sqlite"));
  }

  /**
   * Gives the databases that the conformance plans run on: the one that the property names, or every one when it
   * names none.
   */
  static List<TestDatabase> selected() {
    String name = System.getProperty(PROPERTY);
    return name == null ? Stream.of("sqlite", "postgres", "mariadb", "sqlserver").map(TestDatabase::named).toList()
        : List.of(named(name));
  }

  private static TestDatabase named(String name) {
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

  /**
   * Stops the database, if it runs in a container, so that another one can run in its memory.
   */
  default void stop() {
  }

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
