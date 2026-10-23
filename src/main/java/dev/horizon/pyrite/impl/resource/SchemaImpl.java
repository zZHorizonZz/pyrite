package dev.horizon.pyrite.impl.resource;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.access.v1.Condition;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.authentication.impl.AccountTypeImpl;
import dev.horizon.pyrite.authentication.impl.FactorTypeImpl;
import dev.horizon.pyrite.authentication.impl.FlowType;
import dev.horizon.pyrite.authentication.impl.FlowTypeImpl;
import dev.horizon.pyrite.authentication.impl.IdentifierTypeImpl;
import dev.horizon.pyrite.authentication.impl.SessionTypeImpl;
import dev.horizon.pyrite.crypto.impl.KeyTypeImpl;
import dev.horizon.pyrite.crypto.impl.KeyVersionTypeImpl;
import dev.horizon.pyrite.impl.Adapter;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SchemaImpl implements Schema {

  private final TypeSystem types;
  private final Adapter adapter;
  private final Vertx vertx;
  private final PyriteServiceOptions options;
  private final Map<String, BaseResourceType<?>> byName = new LinkedHashMap<>();
  private final Optional<FlowType> flows;

  SchemaImpl(TypeSystem types, Database database, Adapter adapter, Vertx vertx, PyriteServiceOptions options) {
    this.types = types;
    this.adapter = adapter;
    this.vertx = vertx;
    this.options = options;
    for (DeclaredType declared : types.types()) {
      byName.put(declared.name(), (BaseResourceType<?>) switch (declared.name()) {
        case TypeSystem.KEY -> new KeyTypeImpl(declared, this, database);
        case TypeSystem.KEY_VERSION -> new KeyVersionTypeImpl(declared, this, database, options.root()
            .orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has keys, whose"
                + " material a root key encrypts, but the options of the service have none.")));
        case TypeSystem.ACCOUNT -> new AccountTypeImpl(declared, this, database);
        case TypeSystem.IDENTIFIER -> new IdentifierTypeImpl(declared, this, database);
        case TypeSystem.FACTOR -> new FactorTypeImpl(declared, this, database);
        case TypeSystem.SESSION -> new SessionTypeImpl(declared, this, database);
        default -> new ApplicationType(declared, this, database);
      });
    }
    this.flows = byName.containsKey(TypeSystem.ACCOUNT) ? Optional.of(new FlowTypeImpl(this, database))
        : Optional.empty();
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
  public FlowType flows() {
    return flows.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " accounts, so it has no flows."));
  }

  @Override
  public ResourceType<?> type(String name) {
    ResourceType<?> type = byName.get(name);
    if (type == null) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + name + " is not declared.");
    }
    return type;
  }

  @Override
  public <T extends ResourceType<?>> T type(String name, Class<T> kind) {
    return kind.cast(type(name));
  }

  public List<BaseResourceType<?>> all() {
    return List.copyOf(byName.values());
  }

  public List<BaseResourceType<?>> children(BaseResourceType<?> parent) {
    return byName.values().stream().filter(child -> child.declaration().parents().contains(parent.name()))
        .toList();
  }

  @Override
  public Future<Optional<Resource>> get(String name) {
    return types.typeOf(name).map(type -> byName.get(type.name()).get(name).map(found -> found.map(Resource.class::cast)))
        .orElseGet(() -> Future.succeededFuture(Optional.empty()));
  }

  @Override
  public Future<Resource> require(String name) {
    return get(name).map(found -> found.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND, "The resource "
        + name + " does not exist.")));
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

  @Override
  public Future<Long> purge() {
    Instant now = options.clock().instant();
    Future<Long> purged = Future.succeededFuture(0L);
    for (BaseResourceType<?> type : byName.values()) {
      purged = purged.compose(count -> type.purge(now).map(more -> count + more));
    }
    return purged;
  }
}
