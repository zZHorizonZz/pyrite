package dev.horizon.pyrite;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import dev.horizon.cinder.sqlite.SqliteDriver;
import dev.horizon.pyrite.access.AccessService;
import dev.horizon.pyrite.access.v1.AccessGrpcClient;
import dev.horizon.pyrite.authentication.AuthenticationService;
import dev.horizon.pyrite.authentication.v1.AuthenticationGrpcClient;
import dev.horizon.pyrite.crypto.CryptographyService;
import dev.horizon.pyrite.crypto.v1.CryptographyGrpcClient;
import dev.horizon.pyrite.data.DataService;
import dev.horizon.pyrite.data.v1.DataGrpcClient;
import dev.horizon.pyrite.schema.SchemaService;
import dev.horizon.pyrite.schema.v1.SchemaGrpcClient;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import dev.horizon.spectrum.OpenFgaService;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;

final class Installation implements AutoCloseable {

  final Vertx vertx;
  final MovingClock clock;
  final DataGrpcClient data;
  final AccessGrpcClient access;
  final CryptographyGrpcClient cryptography;
  final AuthenticationGrpcClient authentication;
  final SchemaGrpcClient schema;

  private Installation(Vertx vertx, MovingClock clock, EventBusGrpcClient client) {
    this.vertx = vertx;
    this.clock = clock;
    this.data = DataGrpcClient.create(client);
    this.access = AccessGrpcClient.create(client);
    this.cryptography = CryptographyGrpcClient.create(client);
    this.authentication = AuthenticationGrpcClient.create(client);
    this.schema = SchemaGrpcClient.create(client);
  }

  static Installation start(Instant start) throws Exception {
    Vertx vertx = Vertx.vertx();
    Path file = Files.createTempFile("pyrite", ".db");
    file.toFile().deleteOnExit();
    String url = "jdbc:sqlite:" + file;
    for (String[] migrations : new String[][] { { "acme/migration/sqlite", "acme" }, { "pyrite/migration/**/sqlite",
        "pyrite" }, { "spectrum/migration/**/sqlite", "spectrum" } }) {
      Flyway.configure().dataSource(url, null, null).locations("classpath:" + migrations[0])
          .table(migrations[1] + "_schema_history").baselineOnMigrate(true).load().migrate();
    }
    Pool pool = JDBCPool.pool(vertx, new JDBCConnectOptions().setJdbcUrl(url), new PoolOptions().setMaxSize(1));
    EventBusGrpcServer server = EventBusGrpcServer.server(vertx).await(10, TimeUnit.SECONDS);
    SqliteDriver driver = new SqliteDriver();
    server.addService(OpenFgaService.create(vertx, pool, driver));
    EventBusGrpcClient client = EventBusGrpcClient.client(vertx).await(10, TimeUnit.SECONDS);
    OpenFGAServiceGrpcClient openFga = OpenFGAServiceGrpcClient.create(client);
    AeadConfig.register();
    Aead root = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        .getPrimitive(RegistryConfiguration.get(), Aead.class);
    MovingClock clock = new MovingClock(start);
    PyriteServiceOptions options = new PyriteServiceOptions().withRoot(root).withClock(clock)
        .withAuthentication(new PyriteServiceOptions.Authentication("keys/sessions", "keys/factors",
            new PyriteServiceOptions.RelyingParty("acme.dev", "Acme", List.of("https://acme.dev")), "Acme",
            Duration.ofDays(30), Duration.ofDays(7), Duration.ofMinutes(15), Duration.ofMinutes(10), 3,
            new PyriteServiceOptions.Argon2(1024, 1, 1)));
    server.addService(SchemaService.create(vertx, pool, driver, openFga, options));
    server.addService(DataService.create(vertx, pool, driver, openFga, options));
    server.addService(AccessService.create(vertx, pool, driver, openFga, options));
    server.addService(CryptographyService.create(vertx, pool, driver, openFga, options));
    server.addService(AuthenticationService.create(vertx, pool, driver, openFga, options));
    SchemaGrpcClient.create(client).writeDeclaration(WriteDeclarationRequest.newBuilder()
        .setDeclaration(Acme.declaration()).build()).await(10, TimeUnit.SECONDS);
    return new Installation(vertx, clock, client);
  }

  @Override
  public void close() throws Exception {
    vertx.close().await(10, TimeUnit.SECONDS);
  }
}
