package dev.horizon.pyrite;

import static dev.horizon.pyrite.database.RunningDatabase.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.access.AccessService;
import dev.horizon.pyrite.access.v1.AccessGrpcClient;
import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.authentication.AuthenticationService;
import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.authentication.v1.AuthenticationGrpcClient;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.IdentifierKind;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.authentication.v1.SessionToken;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.crypto.CryptographyService;
import dev.horizon.pyrite.crypto.v1.CryptographyGrpcClient;
import dev.horizon.pyrite.data.DataService;
import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.DataGrpcClient;
import dev.horizon.pyrite.data.v1.GetResourceRequest;
import dev.horizon.pyrite.data.v1.MapValue;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesResponse;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.database.RunningDatabase;
import dev.horizon.pyrite.schema.SchemaService;
import dev.horizon.pyrite.schema.v1.SchemaGrpcClient;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import dev.horizon.spectrum.OpenFgaService;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.grpc.client.InvalidStatusException;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.eventbus.EventBusGrpcClient;
import io.vertx.grpc.eventbus.EventBusGrpcServer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

public final class Pyrite implements AutoCloseable {

  public static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  public static final String ANNE = "user:anne";
  public static final String BOB = "user:bob";
  public static final String CAROL = "user:carol";
  public static final String DAVE = "user:dave";

  public final RunningDatabase database;
  public final Vertx vertx;
  public final MovingClock clock = new MovingClock(START);
  public final PyriteServiceOptions options;
  public final OpenFGAServiceGrpcClient openFga;
  public final SchemaGrpcClient schema;
  public final DataGrpcClient data;
  public final AccessGrpcClient access;
  public final CryptographyGrpcClient cryptography;
  public final AuthenticationGrpcClient authentication;

  private final EventBusGrpcServer server;
  private final EventBusGrpcClient client;

  Pyrite(RunningDatabase database) throws Exception {
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
    options = new PyriteServiceOptions().withRoot(root).withClock(clock)
        .withAuthentication(new PyriteServiceOptions.Authentication("keys/sessions", "keys/factors",
            new PyriteServiceOptions.RelyingParty("acme.dev", "Acme", List.of("https://acme.dev")), "Acme",
            Duration.ofDays(30), Duration.ofDays(7), Duration.ofMinutes(15), Duration.ofMinutes(10), 3,
            new PyriteServiceOptions.Argon2(1024, 1, 1)));
    server.addService(SchemaService.create(vertx, database.pool(), driver, openFga, options));
    server.addService(DataService.create(vertx, database.pool(), driver, openFga, options));
    server.addService(AccessService.create(vertx, database.pool(), driver, openFga, options));
    server.addService(CryptographyService.create(vertx, database.pool(), driver, openFga, options));
    server.addService(AuthenticationService.create(vertx, database.pool(), driver, openFga, options));
    schema = SchemaGrpcClient.create(client);
    data = DataGrpcClient.create(client);
    access = AccessGrpcClient.create(client);
    cryptography = CryptographyGrpcClient.create(client);
    authentication = AuthenticationGrpcClient.create(client);
    schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(Acme.declaration()).build())
        .await(TIMEOUT);
  }

  public Resource create(String subject, String parent, String type, String id, Map<String, Value> fields)
      throws Exception {
    return data.createResource(CreateResourceRequest.newBuilder().setSubject(subject).setParent(parent)
        .setResourceId(id).setResource(Resource.newBuilder().setType(type).putAllFields(fields)).build())
        .await(TIMEOUT);
  }

  public Resource get(String subject, String name) throws Exception {
    return data.getResource(GetResourceRequest.newBuilder().setSubject(subject).setName(name).build())
        .await(TIMEOUT);
  }

  public List<String> query(QueryResourcesRequest request) throws Exception {
    List<String> names = new ArrayList<>();
    String token = "";
    do {
      QueryResourcesResponse page = data.queryResources(request.toBuilder().setPageToken(token).build())
          .await(TIMEOUT);
      page.getResourcesList().forEach(resource -> names.add(resource.getName()));
      token = page.getNextPageToken();
    } while (!token.isEmpty());
    return names;
  }

  public Grant grant(String subject, String object, String relation, String grantee) throws Exception {
    return access.createGrant(CreateGrantRequest.newBuilder().setSubject(subject).setGrant(Grant.newBuilder()
        .setObject(object).setRelation(relation).setGrantee(grantee)).build()).await(TIMEOUT);
  }

  public boolean check(String principal, String object, String permission, Struct context) throws Exception {
    return access.check(CheckRequest.newBuilder().setSubject(principal).setObject(object)
        .setPermission(permission).setContext(context).build()).await(TIMEOUT).getAllowed();
  }

  public Resource key(String parent, String id, String purpose, String algorithm) throws Exception {
    return create(ANNE, parent, "pyrite.dev/Key", id, Map.of("purpose", text(purpose), "algorithm",
        text(algorithm)));
  }

  public void accounts() throws Exception {
    create(ANNE, "", "acme.dev/Organization", "acme", Map.of());
    key("organizations/acme", "sessions", "MAC", "HMAC_SHA256");
    key("organizations/acme", "factors", "ENCRYPTION", "AES256_GCM");
  }

  public void account(String id, String email, String password) throws Exception {
    String subject = "user:" + id;
    create(subject, "", "pyrite.dev/Account", id, Map.of("kind", text("HUMAN")));
    create(subject, "accounts/" + id, "pyrite.dev/Identifier", id + "-email", Map.of("kind", text("EMAIL"),
        "value", text(email)));
    answer(start(subject, "enrol-password", ""), PasswordAnswer.newBuilder().setPassword(password).build());
  }

  public void verify(String subject, String email) throws Exception {
    Flow flow = start(subject, "verify", email);
    answer(flow, CodeAnswer.newBuilder().setCode(flow.getDeliveries(0).getCode()).build());
  }

  public String signIn(String email, String password) throws Exception {
    return token(answer(start("", "sign-in", email), PasswordAnswer.newBuilder().setPassword(password).build()));
  }

  public Flow start(String subject, String definition, String email) throws Exception {
    StartFlowRequest.Builder request = StartFlowRequest.newBuilder().setSubject(subject).setDefinition(definition);
    if (!email.isEmpty()) {
      request.setIdentifierKind(IdentifierKind.EMAIL).setIdentifier(email);
    }
    return authentication.startFlow(request.build()).await(TIMEOUT);
  }

  public Flow answer(Flow flow, Message answer) throws Exception {
    return authentication.continueFlow(ContinueFlowRequest.newBuilder().setFlow(flow.getName())
        .setRevision(flow.getRevision()).setRequestId(UUID.randomUUID().toString()).setAnswer(Any.pack(answer))
        .build()).await(TIMEOUT);
  }

  public Principal authenticate(String token) throws Exception {
    return authentication.authenticate(AuthenticateRequest.newBuilder().setSessionToken(token).build())
        .await(TIMEOUT);
  }

  public static String token(Flow done) throws Exception {
    return done.getResult().getIssued(0).unpack(SessionToken.class).getToken();
  }

  public static Value text(String text) {
    return Value.newBuilder().setStringValue(text).build();
  }

  public static Value integer(long integer) {
    return Value.newBuilder().setIntegerValue(integer).build();
  }

  public static Value labels(String key, String value) {
    return Value.newBuilder().setMapValue(MapValue.newBuilder().putFields(key, text(value))).build();
  }

  public static Struct at(String time) {
    return Struct.newBuilder().putFields("current_time", com.google.protobuf.Value.newBuilder()
        .setStringValue(time).build()).build();
  }

  public static GrpcStatus status(ThrowingCallable call) {
    GrpcStatus[] status = new GrpcStatus[1];
    assertThatThrownBy(call).isInstanceOfSatisfying(InvalidStatusException.class,
        failure -> status[0] = failure.actualStatus());
    return status[0];
  }

  @Override
  public void close() throws Exception {
    client.close().await(TIMEOUT);
    server.close().await(TIMEOUT);
  }
}
