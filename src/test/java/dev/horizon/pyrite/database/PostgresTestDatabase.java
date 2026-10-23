package dev.horizon.pyrite.database;

import dev.horizon.cinder.Driver;
import dev.horizon.cinder.postgres.PostgresDriver;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresTestDatabase implements TestDatabase {

  private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(
      System.getProperty("pyrite.test.postgres.image", "postgres:17-alpine"));

  @Override
  public String name() {
    return "PostgreSQL";
  }

  @Override
  public Location start() {
    CONTAINER.start();
    return new Location(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(), CONTAINER.getPassword());
  }

  @Override
  public String migrations() {
    return "postgresql";
  }

  @Override
  public Driver driver() {
    return new PostgresDriver();
  }

  @Override
  public Pool pool(Vertx vertx) {
    return PgBuilder.pool().connectingTo(new PgConnectOptions().setHost(CONTAINER.getHost())
        .setPort(CONTAINER.getMappedPort(5432)).setDatabase(CONTAINER.getDatabaseName())
        .setUser(CONTAINER.getUsername()).setPassword(CONTAINER.getPassword())).using(vertx).build();
  }
}
