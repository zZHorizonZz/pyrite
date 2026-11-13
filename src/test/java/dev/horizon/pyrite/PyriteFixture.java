package dev.horizon.pyrite;

import static dev.horizon.basepoint.expression.Expression.column;
import static dev.horizon.basepoint.expression.Expression.subtract;
import static dev.horizon.basepoint.filter.Filter.eq;
import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import dev.horizon.basepoint.UpdateQuery;
import dev.horizon.basepoint.filter.Filter;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Driver;
import dev.horizon.cinder.protobuf.ProtobufExtension;
import dev.horizon.pyrite.access.v1.AccessGrpcClient;
import dev.horizon.pyrite.authentication.v1.AuthenticationGrpcClient;
import dev.horizon.pyrite.conformance.Transport;
import dev.horizon.pyrite.crypto.v1.CryptographyGrpcClient;
import dev.horizon.pyrite.database.RunningDatabase;
import dev.horizon.pyrite.schema.v1.SchemaGrpcClient;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import dev.horizon.pyrite.storage.DeclarationBackend;
import dev.horizon.pyrite.storage.ExchangeBackend;
import dev.horizon.pyrite.storage.KeyBackend;
import dev.horizon.pyrite.storage.SessionBackend;
import dev.horizon.spectrum.OpenFgaService;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.grpc.client.GrpcClient;
import io.vertx.grpc.client.GrpcClientOptions;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import io.vertx.grpc.server.GrpcServer;
import io.vertx.grpc.server.GrpcServerOptions;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

public final class PyriteFixture implements AutoCloseable {

  public static final String ANNE = "user:anne";
  public static final String BOB = "user:bob";
  public static final String CAROL = "user:carol";
  public static final String DAVE = "user:dave";

  /**
   * The largest message over HTTP, which a plaintext of a mebibyte and its ciphertext fit in; gRPC on Vert.x takes
   * 256 KiB unless its options say more.
   */
  private static final int MAX_MESSAGE_BYTES = 16 << 20;

  public final RunningDatabase database;
  public final Vertx vertx;
  public final PyriteOptions options;
  public final OpenFGAServiceGrpcClient openFga;
  public final SchemaGrpcClient schema;
  public final AccessGrpcClient access;
  public final CryptographyGrpcClient cryptography;
  public final AuthenticationGrpcClient authentication;
  public final Pyrite service;
  public final FakeProvider provider;

  private final Database rows;
  private final EventBusGrpcServer server;
  private final EventBusGrpcClient client;
  private final HttpServer http;
  private final GrpcClient httpClient;

  public PyriteFixture(RunningDatabase database) throws Exception {
    this(database, Transport.EVENT_BUS);
  }

  /**
   * Starts Pyrite on a database, with its services on a transport. OpenFGA is on the event bus either way.
   */
  public PyriteFixture(RunningDatabase database, Transport transport) throws Exception {
    this.database = database;
    database.empty();
    vertx = database.vertx();
    Driver driver = database.driver();
    server = EventBusGrpcServer.server(vertx).await(TIMEOUT);
    client = EventBusGrpcClient.client(vertx).await(TIMEOUT);
    server.addService(OpenFgaService.create(vertx, database.pool(), driver));
    openFga = OpenFGAServiceGrpcClient.create(client);
    AeadConfig.register();
    Aead root = KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        .getPrimitive(RegistryConfiguration.get(), Aead.class);
    rows = Database.builder(database.pool(), driver).extension(new ProtobufExtension()).build();
    provider = FakeProvider.start(vertx);
    options = new PyriteOptions().setKeyOptions(new KeyOptions().setRoot(root))
        .setAuthenticationOptions(new AuthenticationOptions().setMacKey("keys/sessions").setEncryptionKey("keys/factors")
            .setAccessTokenKey("keys/access")
            .addProvider(new ProviderOptions().setName("oidc").setAuth(provider.openIdConnect(vertx))
                .setRedirectUri("https://acme.dev/callback/oidc").addScope("openid").addScope("email"))
            .addProvider(new ProviderOptions().setName("oauth").setAuth(provider.oauth(vertx))
                .setRedirectUri("https://acme.dev/callback/oauth"))
            .setRelyingPartyOptions(new RelyingPartyOptions().setId("acme.dev").setName("Acme")
                .addOrigin("https://acme.dev"))
            .setIssuer("Acme").setMaxAnswers(3).setArgon2Options(new Argon2Options().setMemory(1024).setIterations(1)
                .setParallelism(1)));
    Pyrite pyrite = Pyrite.pyrite(vertx, database.pool(), driver, openFga, options);
    if (transport == Transport.HTTP) {
      GrpcServer grpc = GrpcServer.server(vertx, new GrpcServerOptions().setMaxMessageSize(MAX_MESSAGE_BYTES));
      service = pyrite.bind(grpc);
      http = vertx.createHttpServer().requestHandler(grpc).listen(0, "localhost").await(TIMEOUT);
      httpClient = GrpcClient.client(vertx, new GrpcClientOptions().setMaxMessageSize(MAX_MESSAGE_BYTES));
      SocketAddress address = SocketAddress.inetSocketAddress(http.actualPort(), "localhost");
      schema = SchemaGrpcClient.create(httpClient, address);
      access = AccessGrpcClient.create(httpClient, address);
      cryptography = CryptographyGrpcClient.create(httpClient, address);
      authentication = AuthenticationGrpcClient.create(httpClient, address);
    } else {
      service = pyrite.bind(server);
      http = null;
      httpClient = null;
      schema = SchemaGrpcClient.create(client);
      access = AccessGrpcClient.create(client);
      cryptography = CryptographyGrpcClient.create(client);
      authentication = AuthenticationGrpcClient.create(client);
    }
    schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(Acme.declaration()).build())
        .await(TIMEOUT);
  }

  /**
   * Makes a session, an exchange or a key older, as if the time went on: it moves the times of its row back. The
   * database is Pyrite's clock, so a test changes the rows, not the time.
   *
   * @param name the name of the session, exchange or key
   * @param by how much older
   */
  public void older(String name, Duration by) throws Exception {
    if (name.startsWith("sessions/")) {
      older(new SessionBackend(rows), eq("name", name), by, "create_time", "expire_time", "idle_expire_time");
    } else if (name.startsWith("exchanges/")) {
      older(new ExchangeBackend(rows), eq("id", name.substring("exchanges/".length())), by, "expire_time",
          "nonce_expire_time");
    } else if (name.startsWith("keys/")) {
      older(new KeyBackend(rows), eq("name", name), by, "create_time", "next_rotation_time");
    } else {
      throw new IllegalArgumentException("Pyrite keeps no times of " + name);
    }
  }

  /**
   * Makes the declaration of an id older, as {@link #older(String, Duration)} does.
   */
  public void olderDeclaration(String id, Duration by) throws Exception {
    older(new DeclarationBackend(rows), eq("id", id), by, "create_time");
  }

  /**
   * Deletes every declaration, as if Pyrite had none yet.
   */
  public void forgetDeclarations() throws Exception {
    DeclarationBackend declarations = new DeclarationBackend(rows);
    declarations.execute(declarations.delete().all()).await(TIMEOUT);
  }

  private static void older(Backend<?, ?> backend, Filter row, Duration by, String... times) throws Exception {
    UpdateQuery update = backend.update();
    for (String time : times) {
      update = update.set(time, subtract(column(time), by));
    }
    backend.execute(update.where(row)).await(TIMEOUT);
  }

  public static GrpcStatus status(ThrowingCallable call) {
    GrpcStatus[] status = new GrpcStatus[1];
    assertThatThrownBy(call).isInstanceOfSatisfying(InvalidStatusException.class,
        failure -> status[0] = failure.actualStatus());
    return status[0];
  }

  @Override
  public void close() throws Exception {
    if (http != null) {
      httpClient.close().await(TIMEOUT);
      http.close().await(TIMEOUT);
    }
    client.close().await(TIMEOUT);
    server.close().await(TIMEOUT);
    provider.close();
  }
}
