package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.access.v1.Condition;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.authentication.impl.AccountTypeImpl;
import dev.horizon.pyrite.authentication.impl.FlowType;
import dev.horizon.pyrite.authentication.impl.FlowTypeImpl;
import dev.horizon.pyrite.crypto.impl.KeyTypeImpl;
import dev.horizon.pyrite.impl.Adapter;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.storage.NodeBackend;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Clock;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SchemaImpl implements Schema {

  private static final Pattern ID = Pattern.compile("[a-z]([a-z0-9-]{0,61}[a-z0-9])?");

  private final TypeSystem types;
  private final Adapter adapter;
  private final Vertx vertx;
  private final PyriteServiceOptions options;
  private final NodeBackend nodes;
  private final Optional<KeyTypeImpl> keys;
  private final Optional<AccountTypeImpl> accounts;
  private final Optional<FlowType> flows;

  SchemaImpl(TypeSystem types, Database database, Adapter adapter, Vertx vertx, PyriteServiceOptions options) {
    this.types = types;
    this.adapter = adapter;
    this.vertx = vertx;
    this.options = options;
    this.nodes = new NodeBackend(database);
    this.keys = types.type(TypeSystem.KEY).map(declared -> new KeyTypeImpl(this, declared, database, options.root()
        .orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has keys, whose"
            + " material a root key encrypts, but the options of the service have none."))));
    this.accounts = types.type(TypeSystem.ACCOUNT).map(declared -> new AccountTypeImpl(this, declared, database));
    this.flows = accounts.map(ignored -> new FlowTypeImpl(this, database));
  }

  @Override
  public TypeSystem types() {
    return types;
  }

  @Override
  public Adapter adapter() {
    return adapter;
  }

  @Override
  public Vertx vertx() {
    return vertx;
  }

  @Override
  public Clock clock() {
    return options.clock();
  }

  @Override
  public PyriteServiceOptions options() {
    return options;
  }

  @Override
  public DeclaredType type(String name) {
    return types.type(name).orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + name
        + " is not declared."));
  }

  @Override
  public KeyTypeImpl keys() {
    return keys.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " keys; declare the type " + TypeSystem.KEY + "."));
  }

  @Override
  public AccountTypeImpl accounts() {
    return accounts.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " accounts; declare the type " + TypeSystem.ACCOUNT + "."));
  }

  @Override
  public FlowType flows() {
    return flows.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " accounts, so it has no flows."));
  }

  @Override
  public Future<Resource> create(Subject creator, String name, String parent, Struct context) {
    DeclaredType type = types.typeOf(name).orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The"
        + " name " + name + " is not of a declared type: its first segment is no type's plural."));
    if (!ID.matcher(type.id(name)).matches()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The id of " + name + " does not match " + ID.pattern()
          + ".");
    }
    Resource created = new Resource(this, type, name);
    return place(type, parent)
        .compose(above -> above.isEmpty() ? Future.succeededFuture(above)
            : above.get().require(creator, type.permissions().getCreate(), context).map(above))
        .compose(above -> nodes.existsById(name).compose(known -> {
          if (known) {
            throw new PyriteException(GrpcStatus.ALREADY_EXISTS, "The resource " + name + " is known already.");
          }
          Tables.Node.Builder node = Tables.Node.newBuilder().setName(name).setType(type.name())
              .setCreateTime(clock().instant().toString());
          above.ifPresent(found -> node.setParent(found.name()));
          return nodes.insert(node.build()).map(above);
        }))
        .compose(above -> above.isEmpty() ? Future.succeededFuture()
            : adapter.write(TupleKey.newBuilder().setUser(above.get().object()).setRelation(TypeSystem.PARENT)
                .setObject(created.object()).build()))
        .compose(ignored -> type.creator().isEmpty() ? Future.succeededFuture()
            : adapter.write(TupleKey.newBuilder().setUser(creator.toString()).setRelation(type.creator())
                .setObject(created.object()).build()))
        .map(created);
  }

  @Override
  public Future<Void> forget(String name) {
    return require(name).compose(found -> nodes.occupied(name).compose(occupied -> {
      if (occupied) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The resource " + name + " has resources under"
            + " it; forget them first.");
      }
      return found.forget();
    })).compose(ignored -> nodes.deleteById(name)).mapEmpty();
  }

  @Override
  public Future<Optional<Resource>> get(String name) {
    return types.typeOf(name)
        .map(type -> nodes.findById(name).map(node -> node.filter(found -> found.getType().equals(type.name()))
            .map(found -> new Resource(this, type, name))))
        .orElseGet(() -> Future.succeededFuture(Optional.empty()));
  }

  @Override
  public Future<Resource> require(String name) {
    return get(name).map(found -> found.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND, "The resource "
        + name + " is not known.")));
  }

  @Override
  public Optional<Grant> grant(TupleKey tuple) {
    int colon = tuple.getObject().indexOf(':');
    return types.singular(tuple.getObject().substring(0, colon)).map(type -> {
      Grant.Builder grant = Grant.newBuilder().setObject(type.resource(tuple.getObject().substring(colon + 1)))
          .setRelation(tuple.getRelation()).setGrantee(tuple.getUser());
      if (tuple.hasCondition()) {
        grant.setCondition(Condition.newBuilder().setName(tuple.getCondition().getName())
            .setContext(tuple.getCondition().getContext()));
      }
      return grant.build();
    });
  }

  private Future<Optional<Resource>> place(DeclaredType type, String parent) {
    if (type.parents().isEmpty()) {
      if (!parent.isEmpty()) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name() + " has no parents, but the"
            + " request names " + parent + ".");
      }
      return Future.succeededFuture(Optional.empty());
    }
    if (parent.isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A resource of the type " + type.name() + " is under"
          + " one of " + type.parents() + ", but the request names no parent.");
    }
    return require(parent).map(found -> {
      if (!type.parents().contains(found.type().name())) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A resource of the type " + type.name() + " cannot be"
            + " under " + parent + ".");
      }
      return Optional.of(found);
    });
  }
}
