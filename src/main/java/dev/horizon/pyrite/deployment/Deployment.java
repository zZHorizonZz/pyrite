package dev.horizon.pyrite.deployment;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Driver;
import dev.horizon.cinder.protobuf.ProtobufExtension;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteOptions;
import dev.horizon.pyrite.access.OpenFga;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.flow.FlowInterceptor;
import dev.horizon.pyrite.identity.AccountObserver;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import dev.horizon.pyrite.session.SessionObserver;
import dev.horizon.pyrite.storage.DatabaseTime;
import dev.horizon.pyrite.storage.DeclarationBackend;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.CreateStoreResponse;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.sqlclient.Pool;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The declarations of one database: the latest one, which every call reads, and the writing of new ones.
 *
 * <p>
 * The services of one {@code Pyrite} share one deployment. Every call reads the latest declaration's id from the table
 * {@code pyrite_declaration}, so a declaration that one Pyrite writes takes effect at the next call of every Pyrite on
 * the database. The deployment keeps the schemas of the last four declarations that it read, so a call compiles a
 * declaration only the first time.
 */
public final class Deployment {

  private static final int SCHEMAS = 4;

  private static final String STORE = "pyrite";

  private final Vertx vertx;
  private final Database database;
  private final OpenFGAServiceGrpcClient openFga;
  private final PyriteOptions options;
  private final DeclarationBackend declarations;
  /**
   * The schemas of the declarations last read; a declaration that is no longer the latest drops out.
   */
  private final Map<String, Schema> schemas = Collections.synchronizedMap(new LinkedHashMap<>(SCHEMAS, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Schema> eldest) {
      return size() > SCHEMAS;
    }
  });
  private final FlowInterceptor interceptor;
  private final AccountObserver accountObserver;
  private final SessionObserver sessionObserver;

  public Deployment(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteOptions options, FlowInterceptor interceptor, AccountObserver accountObserver,
      SessionObserver sessionObserver) {
    this.vertx = vertx;
    this.interceptor = interceptor;
    this.accountObserver = accountObserver;
    this.sessionObserver = sessionObserver;
    this.database = Database.builder(pool, driver).extension(new ProtobufExtension())
        .pageTokenKey(options.getPageTokenKey()).build();
    this.openFga = openFga;
    this.options = options;
    this.declarations = new DeclarationBackend(database);
  }

  /**
   * Gives the schema of the latest declaration, which a call works on.
   *
   * @return the schema, or a failure with {@code FAILED_PRECONDITION} before the first declaration
   */
  public Future<Schema> schema() {
    return declarations.latest().map(found -> {
      Tables.Declaration row = found.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "No"
          + " declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration."));
      return schemas.computeIfAbsent(row.getId(), id -> new Schema(new TypeSystem(declaration(row)), database,
          new OpenFga(openFga, row.getStoreId(), row.getModelId(), options.getTuplesPerRead()), vertx, options,
          interceptor, accountObserver, sessionObserver));
    });
  }

  /**
   * Writes a declaration and makes it the latest: it checks it, writes its authorization model to the OpenFGA store,
   * which the first declaration creates under the name {@code pyrite}, and stores it.
   *
   * @param declaration the declaration
   * @return the new id of the declaration, or a failure with {@code INVALID_ARGUMENT} for a declaration that breaks a
   * rule, and with {@code FAILED_PRECONDITION} for keys without a root key, or accounts without {@code pyrite.dev/Key}
   * or the options of authentication
   */
  public Future<String> write(Declaration declaration) {
    TypeSystem types;
    try {
      types = new TypeSystem(declaration);
    } catch (IllegalArgumentException invalid) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, invalid.getMessage()));
    }
    boolean keys = types.type(TypeSystem.KEY).isPresent();
    if (keys && options.getKeyOptions().getRoot() == null) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has keys,"
          + " whose material a root key seals, but the options name no root key."));
    }
    List<String> missing = options.getAuthenticationOptions().missing();
    if (types.type(TypeSystem.ACCOUNT).isPresent() && (!keys || !missing.isEmpty())) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has accounts,"
          + " which need the type " + TypeSystem.KEY + " and the options of authentication; "
          + (keys ? "these lack " + String.join(", ", missing) + "." : "it has no keys.")));
    }
    String id = UUID.randomUUID().toString();
    return declarations.latest()
        .compose(latest -> latest.isPresent() ? Future.succeededFuture(latest.get().getStoreId())
            : openFga.createStore(CreateStoreRequest.newBuilder().setName(STORE).build())
                .map(CreateStoreResponse::getId))
        .compose(store -> openFga.writeAuthorizationModel(types.model(store))
            .compose(model -> declarations.insert(Tables.Declaration.newBuilder().setId(id)
                .setDeclaration(declaration.toByteString()).setStoreId(store)
                .setModelId(model.getAuthorizationModelId()).build())))
        .map(id);
  }

  /**
   * Reads a declaration as it was written.
   *
   * @param id the id of the declaration; empty for the latest
   * @return the declaration with its id and the time that the database stored it, or a failure with {@code NOT_FOUND}
   * for an id that no declaration has, or before the first declaration
   */
  public Future<ReadDeclarationResponse> read(String id) {
    Future<Tables.Declaration> found = id.isEmpty()
        ? declarations.latest().map(latest -> latest.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
            "No declaration is written.")))
        : declarations.findById(id).map(row -> row.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
            "The declaration " + id + " does not exist.")));
    return found.map(row -> {
      return ReadDeclarationResponse.newBuilder().setId(row.getId()).setDeclaration(declaration(row))
          .setCreateTime(DatabaseTime.timestamp(row.getCreateTime()))
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
