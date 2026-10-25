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
import dev.horizon.pyrite.access.v1.CreateObjectRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListObjectsRequest;
import dev.horizon.pyrite.authentication.AuthenticationService;
import dev.horizon.pyrite.authentication.v1.Account;
import dev.horizon.pyrite.authentication.v1.AddIdentifierRequest;
import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.authentication.v1.AuthenticationGrpcClient;
import dev.horizon.pyrite.authentication.v1.CodeAnswer;
import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.CreateAccountRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.PasswordAnswer;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.authentication.v1.SessionToken;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.crypto.CryptographyService;
import dev.horizon.pyrite.crypto.v1.Algorithm;
import dev.horizon.pyrite.crypto.v1.CreateKeyRequest;
import dev.horizon.pyrite.crypto.v1.CryptographyGrpcClient;
import dev.horizon.pyrite.crypto.v1.Key;
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
import java.util.List;
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
    server.addService(AccessService.create(vertx, database.pool(), driver, openFga, options));
    server.addService(CryptographyService.create(vertx, database.pool(), driver, openFga, options));
    server.addService(AuthenticationService.create(vertx, database.pool(), driver, openFga, options));
    schema = SchemaGrpcClient.create(client);
    access = AccessGrpcClient.create(client);
    cryptography = CryptographyGrpcClient.create(client);
    authentication = AuthenticationGrpcClient.create(client);
    schema.writeDeclaration(WriteDeclarationRequest.newBuilder().setDeclaration(Acme.declaration()).build())
        .await(TIMEOUT);
  }

  public String create(String subject, String name, String parent) throws Exception {
    access.createObject(CreateObjectRequest.newBuilder().setSubject(subject).setName(name).setParent(parent).build())
        .await(TIMEOUT);
    return name;
  }

  public List<String> objects(String subject, String type, String permission, Struct context) throws Exception {
    return access.listObjects(ListObjectsRequest.newBuilder().setSubject(subject).setType(type)
        .setPermission(permission).setContext(context).build()).await(TIMEOUT).getNamesList();
  }

  public Grant grant(String subject, String object, String relation, String grantee) throws Exception {
    return access.createGrant(CreateGrantRequest.newBuilder().setSubject(subject).setGrant(Grant.newBuilder()
        .setObject(object).setRelation(relation).setGrantee(grantee)).build()).await(TIMEOUT);
  }

  public boolean check(String principal, String object, String permission, Struct context) throws Exception {
    return access.check(CheckRequest.newBuilder().setSubject(principal).setObject(object)
        .setPermission(permission).setContext(context).build()).await(TIMEOUT).getAllowed();
  }

  public Key key(String parent, String id, Algorithm algorithm) throws Exception {
    return cryptography.createKey(CreateKeyRequest.newBuilder().setSubject(ANNE).setKeyId(id).setParent(parent)
        .setAlgorithm(algorithm).build()).await(TIMEOUT);
  }

  public void accounts() throws Exception {
    create(ANNE, "organizations/acme", "");
    key("organizations/acme", "sessions", Algorithm.HMAC_SHA256);
    key("organizations/acme", "factors", Algorithm.AES256_GCM);
  }

  public void account(String id, String email, String password) throws Exception {
    String subject = "user:" + id;
    authentication.createAccount(CreateAccountRequest.newBuilder().setSubject(subject).setAccountId(id)
        .setKind(Account.Kind.HUMAN).build()).await(TIMEOUT);
    authentication.addIdentifier(AddIdentifierRequest.newBuilder().setSubject(subject).setAccount("accounts/" + id)
        .setKind("email").setValue(email).build()).await(TIMEOUT);
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
      request.setIdentifierKind("email").setIdentifier(email);
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
