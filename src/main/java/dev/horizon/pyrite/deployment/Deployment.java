package dev.horizon.pyrite.deployment;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.horizon.cinder.Database;
import dev.horizon.cinder.Driver;
import dev.horizon.cinder.DuplicateKeyException;
import dev.horizon.cinder.protobuf.ProtobufExtension;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteOptions;
import dev.horizon.pyrite.access.OpenFga;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.exchange.ExchangeInterceptor;
import dev.horizon.pyrite.observer.LifecycleObserver;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import dev.horizon.pyrite.schema.v1.Declaration;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import dev.horizon.pyrite.schema.v1.Type;
import dev.horizon.pyrite.storage.DatabaseTime;
import dev.horizon.pyrite.storage.DeclarationBackend;
import dev.horizon.pyrite.storage.NodeBackend;
import dev.horizon.spectrum.openfga.v1.CreateStoreRequest;
import dev.horizon.spectrum.openfga.v1.CreateStoreResponse;
import dev.horizon.spectrum.openfga.v1.DeleteStoreRequest;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.sqlclient.Pool;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The declarations of one database: the latest one, which every call reads, and the writing of new ones.
 *
 * <p>
 * The services of one {@code Pyrite} share one deployment. Every call reads the latest declaration's id from the table
 * {@code pyrite_declaration}, so a declaration that one Pyrite writes takes effect at the next call of every Pyrite on
 * the database. The deployment keeps the schemas of the last four declarations that it read, so a call compiles a
 * declaration only the first time.
 *
 * <p>
 * Each declaration is stored on the one that was the latest, and the database stores no two on the same one. Of two
 * Pyrites that write at once, the one whose row comes second writes its declaration again on the other's, with its
 * store of grants, so that the first declaration creates one store.
 */
public final class Deployment {

  private static final int MAX_SCHEMAS = 4;

  /** The attempts of a write, each on the declaration that another Pyrite stored during the attempt before. */
  private static final int MAX_ATTEMPTS = 3;

  private static final String STORE = "pyrite";

  private final Vertx vertx;
  private final Database database;
  private final OpenFGAServiceGrpcClient openFga;
  private final PyriteOptions options;
  private final DeclarationBackend declarations;
  private final NodeBackend nodes;
  /**
   * The schemas of the declarations last read; a declaration that is no longer the latest drops out.
   */
  private final Map<String, Schema> schemas = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Schema> eldest) {
      return size() > MAX_SCHEMAS;
    }
  });
  private final ExchangeInterceptor interceptor;
  private final LifecycleObserver<dev.horizon.pyrite.authentication.v1.Account> accountObserver;
  private final LifecycleObserver<dev.horizon.pyrite.authentication.v1.Session> sessionObserver;

  public Deployment(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteOptions options, ExchangeInterceptor interceptor,
      LifecycleObserver<dev.horizon.pyrite.authentication.v1.Account> accountObserver,
      LifecycleObserver<dev.horizon.pyrite.authentication.v1.Session> sessionObserver) {
    this.vertx = vertx;
    this.interceptor = interceptor;
    this.accountObserver = accountObserver;
    this.sessionObserver = sessionObserver;
    this.database = Database.builder(pool, driver).extension(new ProtobufExtension())
        .pageTokenKey(options.getPageTokenKey()).build();
    this.openFga = openFga;
    this.options = options;
    this.declarations = new DeclarationBackend(database);
    this.nodes = new NodeBackend(database);
  }

  /**
   * Gives the schema of the latest declaration, which a call works on.
   *
   * @return the schema, or a failure with {@code FAILED_PRECONDITION} before the first declaration
   */
  public Future<Schema> schema() {
    return declarations.latest().map(latest -> {
      Tables.Declaration row = latest.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "No"
          + " declaration is written; write one with pyrite.schema.v1.Schema.WriteDeclaration."));
      return schemas.computeIfAbsent(row.getId(), id -> new Schema(new TypeSystem(declaration(row)), database,
          new OpenFga(openFga, row.getStoreId(), row.getModelId(), options.getTuplesPerRead()), vertx, options,
          interceptor, accountObserver, sessionObserver));
    });
  }

  /**
   * Writes a declaration and makes it the latest: it checks it, checks that the resources that Pyrite knows stay in
   * reach, writes its authorization model to the store of grants, which the first declaration creates under the name
   * {@code pyrite}, and stores it.
   *
   * @param declaration the declaration
   * @return the new id of the declaration, or a failure with {@code INVALID_ARGUMENT} for a declaration that breaks a
   * rule, with {@code FAILED_PRECONDITION} for keys without a root key, accounts without {@code pyrite.dev/Key} or the
   * options of authentication, or a change of a type whose resources Pyrite knows, and with {@code ABORTED} when other
   * declarations are stored at the same time at each attempt
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
    return declarations.latest().compose(latest -> write(declaration, types, latest, MAX_ATTEMPTS));
  }

  /**
   * Reads a declaration as it was written.
   *
   * @param id the id of the declaration; empty for the latest
   * @return the declaration with its id and the time that the database stored it, or a failure with {@code NOT_FOUND}
   * for an id that no declaration has, or before the first declaration
   */
  public Future<ReadDeclarationResponse> read(String id) {
    Future<Tables.Declaration> entry = id.isEmpty()
        ? declarations.latest().map(latest -> latest.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
            "No declaration is written.")))
        : declarations.findById(id).map(match -> match.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
            "The declaration " + id + " does not exist.")));
    return entry.map(row -> ReadDeclarationResponse.newBuilder().setId(row.getId()).setDeclaration(declaration(row))
        .setCreateTime(DatabaseTime.timestamp(row.getCreateTime())).build());
  }

  /**
   * Stores a declaration on the latest one, or as the first: it checks that the resources that Pyrite knows stay in
   * reach, writes the model to the store of the latest one, or to a new store for the first, and stores the row. When
   * another Pyrite stored a declaration on the same latest one, it deletes a store that it created, and writes again
   * on the last declaration stored since.
   *
   * @param declaration the declaration
   * @param types the declaration, compiled
   * @param latest the latest declaration; empty before the first
   * @param attempts the attempts left
   * @return the new id of the declaration
   */
  private Future<String> write(Declaration declaration, TypeSystem types, Optional<Tables.Declaration> latest,
      int attempts) {
    String previous = latest.map(Tables.Declaration::getId).orElse("");
    Future<Void> reach = latest.isEmpty() ? Future.succeededFuture()
        : Future.future(promise -> keepsResources(declaration(latest.get()), declaration).onComplete(promise));
    return reach.compose(ignored -> latest.isPresent() ? Future.succeededFuture(latest.get().getStoreId())
        : openFga.createStore(CreateStoreRequest.newBuilder().setName(STORE).build()).map(CreateStoreResponse::getId))
        .compose(store -> {
          String id = UUID.randomUUID().toString();
          return openFga.writeAuthorizationModel(types.model(store))
              .compose(model -> declarations.insert(Tables.Declaration.newBuilder().setId(id)
                  .setDeclaration(declaration.toByteString()).setStoreId(store)
                  .setModelId(model.getAuthorizationModelId()).setPreviousId(previous).build()))
              .map(id)
              .recover(failure -> {
                if (!(failure instanceof DuplicateKeyException)) {
                  return Future.failedFuture(failure);
                }
                Future<Void> deletion = latest.isPresent() ? Future.succeededFuture()
                    : openFga.deleteStore(DeleteStoreRequest.newBuilder().setStoreId(store).build())
                        .<Void> mapEmpty().otherwiseEmpty();
                if (attempts <= 1) {
                  return deletion.compose(ignored -> Future.failedFuture(new PyriteException(GrpcStatus.ABORTED,
                      "Other declarations were written at the same time as this one; write it again.")));
                }
                return deletion.compose(ignored -> lastAfter(previous))
                    .compose(last -> last.isPresent() ? Future.succeededFuture(last) : declarations.latest())
                    .compose(last -> write(declaration, types, last, attempts - 1));
              });
        });
  }

  /**
   * Reads the last declaration of those stored on another, one on the other.
   *
   * @param previous the id of the other declaration; empty for none, to start from the first
   * @return the declaration that none is stored on yet, or empty when none is stored on the other
   */
  private Future<Optional<Tables.Declaration>> lastAfter(String previous) {
    return declarations.next(previous).compose(next -> next.isEmpty() ? Future.succeededFuture(next)
        : lastAfter(next.get().getId()).map(last -> last.or(() -> next)));
  }

  /**
   * Refuses a declaration that leaves resources that Pyrite knows out of reach: one that drops a type of the latest
   * declaration, or changes its plural, its singular name or its parents, while the table {@code pyrite_node} has rows
   * of the type. A resource finds its type by the plural, its object in the store of grants by the singular name, and
   * the resource that it is under among the parents of its type.
   *
   * @param latest the latest declaration
   * @param declaration the new declaration
   * @return a future that completes when no changed type has resources, or a failure with {@code FAILED_PRECONDITION}
   */
  private Future<Void> keepsResources(Declaration latest, Declaration declaration) {
    Map<String, Type> types = new HashMap<>();
    for (Type type : declaration.getTypesList()) {
      types.putIfAbsent(type.getName(), type);
    }
    Future<Void> reach = Future.succeededFuture();
    for (Type type : latest.getTypesList()) {
      Optional<String> change = change(type, Optional.ofNullable(types.get(type.getName())));
      if (change.isPresent()) {
        reach = reach.compose(ignored -> nodes.holds(type.getName())).<Void> compose(any -> any
            ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration "
                + change.get() + ", but Pyrite knows resources of the type; forget them first, or keep the type as"
                + " it is."))
            : Future.succeededFuture());
      }
    }
    return reach;
  }

  /**
   * Tells how a declaration changes what finds the resources of a type.
   *
   * @param type the type in the latest declaration
   * @param next the type in the new declaration; empty when it drops the type
   * @return the change, such as {@code drops the type acme.dev/Project}, or empty when the plural, the singular name
   * and the parents stay
   */
  private static Optional<String> change(Type type, Optional<Type> next) {
    String name = type.getName();
    if (next.isEmpty()) {
      return Optional.of("drops the type " + name);
    }
    if (!next.get().getPlural().equals(type.getPlural())) {
      return Optional.of("changes the plural of the type " + name + " from " + type.getPlural() + " to "
          + next.get().getPlural());
    }
    String singular = type.getDefinition().getType();
    if (!next.get().getDefinition().getType().equals(singular)) {
      return Optional.of("changes the singular name of the type " + name + " from " + singular + " to "
          + next.get().getDefinition().getType());
    }
    if (!Set.copyOf(next.get().getParentsList()).equals(Set.copyOf(type.getParentsList()))) {
      return Optional.of("changes the parents of the type " + name + " from " + type.getParentsList() + " to "
          + next.get().getParentsList());
    }
    return Optional.empty();
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
