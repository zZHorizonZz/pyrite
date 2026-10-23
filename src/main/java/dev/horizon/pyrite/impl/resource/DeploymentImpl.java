package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Driver;
import dev.horizon.cinder.protobuf.ProtobufExtension;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.impl.GrpcAdapter;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.model.TypeSystemImpl;
import dev.horizon.pyrite.impl.storage.DeclarationBackend;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.CreateStoreResponse;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.sqlclient.Pool;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeploymentImpl implements Deployment {

  private static final String STORE = "pyrite";

  private final Vertx vertx;
  private final Database database;
  private final OpenFGAServiceGrpcClient openFga;
  private final PyriteServiceOptions options;
  private final DeclarationBackend declarations;
  private final Map<String, Schema> schemas = new ConcurrentHashMap<>();

  public DeploymentImpl(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    this.vertx = vertx;
    this.database = Database.builder(pool, driver).extension(new ProtobufExtension()).clock(options.clock())
        .pageTokenKey(options.pageTokenKey()).build();
    this.openFga = openFga;
    this.options = options;
    this.declarations = new DeclarationBackend(database);
  }

  @Override
  public Future<Schema> schema() {
    return declarations.latest().map(found -> {
      Tables.Declaration row = found.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "No"
          + " declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration."));
      return schemas.computeIfAbsent(row.getId(), id -> new SchemaImpl(new TypeSystemImpl(declaration(row)), database,
          new GrpcAdapter(openFga, row.getStoreId(), row.getModelId(), options.tuplesPerRead()), vertx, options));
    });
  }

  @Override
  public Future<String> write(Declaration declaration) {
    TypeSystem types;
    try {
      types = new TypeSystemImpl(declaration);
    } catch (IllegalArgumentException invalid) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, invalid.getMessage());
    }
    boolean keys = types.type(TypeSystem.KEY).isPresent();
    if (keys && options.root().isEmpty()) {
      throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has keys, whose material a root"
          + " key encrypts, but the options of the service have none.");
    }
    if (types.type(TypeSystem.ACCOUNT).isPresent() && (options.authentication().isEmpty() || !keys)) {
      throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has accounts, which need the type "
          + TypeSystem.KEY + " and the options of authentication.");
    }
    String id = UUID.randomUUID().toString();
    return declarations.latest()
        .compose(latest -> latest.isPresent() ? Future.succeededFuture(latest.get().getStoreId())
            : openFga.createStore(CreateStoreRequest.newBuilder().setName(STORE).build())
                .map(CreateStoreResponse::getId))
        .compose(store -> openFga.writeAuthorizationModel(types.model(store))
            .compose(model -> declarations.insert(Tables.Declaration.newBuilder().setId(id)
                .setDeclaration(declaration.toByteString()).setStoreId(store)
                .setModelId(model.getAuthorizationModelId())
                .setCreateTime(options.clock().instant().toString()).build())))
        .map(id);
  }

  @Override
  public Future<ReadDeclarationResponse> read(String id) {
    Future<Tables.Declaration> found = id.isEmpty()
        ? declarations.latest().map(latest -> latest.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
            "No declaration is written.")))
        : declarations.findById(id).map(row -> row.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
            "The declaration " + id + " does not exist.")));
    return found.map(row -> {
      Instant created = Instant.parse(row.getCreateTime());
      return ReadDeclarationResponse.newBuilder().setId(row.getId()).setDeclaration(declaration(row))
          .setCreateTime(Timestamp.newBuilder().setSeconds(created.getEpochSecond()).setNanos(created.getNano()))
          .build();
    });
  }

  private static Declaration declaration(Tables.Declaration row) {
    try {
      return Declaration.parseFrom(row.getDeclaration());
    } catch (InvalidProtocolBufferException unreadable) {
      throw new IllegalStateException("The declaration " + row.getId() + " is not a pyrite.schema.v1.Declaration",
          unreadable);
    }
  }
}
