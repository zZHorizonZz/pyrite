package dev.horizon.pyrite.database;

import dev.horizon.cinder.Driver;
import dev.horizon.cinder.sqlserver.SqlServerDriver;
import io.vertx.core.Vertx;
import io.vertx.mssqlclient.MSSQLBuilder;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.sqlclient.Pool;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

public final class SqlServerTestDatabase implements TestDatabase {

  private static final String DATABASE = "pyrite";

  private static final MSSQLServerContainer CONTAINER = new MSSQLServerContainer(
      System.getProperty("pyrite.test.sqlserver.image", "mcr.microsoft.com/mssql/server:2022-latest"))
      .acceptLicense();

  @Override
  public String name() {
    return "SQL Server";
  }

  @Override
  public Location start() {
    CONTAINER.start();
    try (Connection admin = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
        CONTAINER.getPassword()); Statement statement = admin.createStatement()) {
      statement.execute("IF DB_ID('" + DATABASE + "') IS NULL CREATE DATABASE " + DATABASE);
    } catch (SQLException failed) {
      throw new IllegalStateException(failed);
    }
    return new Location(CONTAINER.getJdbcUrl() + ";databaseName=" + DATABASE, CONTAINER.getUsername(),
        CONTAINER.getPassword());
  }

  @Override
  public String migrations() {
    return "sqlserver";
  }

  @Override
  public Driver driver() {
    return new SqlServerDriver();
  }

  @Override
  public Pool pool(Vertx vertx) {
    return MSSQLBuilder.pool().connectingTo(new MSSQLConnectOptions().setHost(CONTAINER.getHost())
        .setPort(CONTAINER.getMappedPort(1433)).setDatabase(DATABASE).setUser(CONTAINER.getUsername())
        .setPassword(CONTAINER.getPassword())).using(vertx).build();
  }
}
