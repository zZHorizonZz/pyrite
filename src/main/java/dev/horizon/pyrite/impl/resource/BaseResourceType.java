package dev.horizon.pyrite.impl.resource;

import static dev.horizon.basepoint.Filter.eq;

import com.google.protobuf.Struct;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.basepoint.Filter;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.Definition;
import dev.horizon.pyrite.impl.storage.ResourceBackend;
import dev.horizon.pyrite.impl.storage.Row;
import dev.horizon.pyrite.schema.v1.Field;
import dev.horizon.pyrite.schema.v1.Permissions;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public abstract class BaseResourceType<R extends Resource> implements ResourceType<R> {

  private static final Pattern ID = Pattern.compile("[a-z]([a-z0-9-]{0,61}[a-z0-9])?");
  private static final Set<String> STANDARD = Set.of(Resource.NAME, Resource.UID, Resource.PARENT, Resource.REVISION,
      Resource.CREATE_TIME, Resource.UPDATE_TIME, Resource.DELETE_TIME, Resource.PURGE_TIME);

  private final DeclaredType declared;
  private final SchemaImpl schema;
  private final ResourceBackend backend;
  private final Set<String> owned;
  private final Set<String> hidden;
  private final Set<String> fixed;
  private final boolean dependent;

  protected BaseResourceType(DeclaredType declared, SchemaImpl schema, Database database, Set<String> owned,
      Set<String> hidden, Set<String> fixed, boolean dependent) {
    this.declared = declared;
    this.schema = schema;
    this.backend = new ResourceBackend(database, declared);
    Set<String> all = new HashSet<>(STANDARD);
    all.addAll(owned);
    this.owned = Set.copyOf(all);
    this.hidden = Set.copyOf(hidden);
    this.fixed = Set.copyOf(fixed);
    this.dependent = dependent;
  }

  protected abstract R resource(Row row);

  @Override
  public DeclaredType declaration() {
    return declared;
  }

  @Override
  public String name() {
    return declared.name();
  }

  @Override
  public Permissions permissions() {
    return declared.permissions();
  }

  @Override
  public SchemaImpl schema() {
    return schema;
  }

  Set<String> pyriteFields() {
    return owned;
  }

  Set<String> secretFields() {
    return hidden;
  }

  Set<String> constantFields() {
    return fixed;
  }

  boolean dependent() {
    return dependent;
  }

  @Override
  public Definition relation(String relation) {
    Definition definition = declared.relations().get(relation);
    if (definition == null) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + name() + " has no relation " + relation
          + ".");
    }
    return definition;
  }

  @Override
  public Object parse(String field, Value value) {
    Field.Kind kind = declared.fields().get(field);
    if (kind == null) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + name() + " has no field " + field + ".");
    }
    Object stored = FieldValue.of(field, kind, value).value();
    if (field.equals(Resource.LABELS) && stored instanceof JsonObject labels) {
      labels.forEach(label -> {
        if (!(label.getValue() instanceof String)) {
          throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The label " + label.getKey() + " has a value"
              + " that is not a string.");
        }
      });
    }
    return stored;
  }

  @Override
  public Future<Optional<R>> get(String name) {
    return first(eq(Resource.NAME, name));
  }

  public Future<Optional<R>> first(Filter where) {
    return backend.first(where).map(row -> row.map(this::resource));
  }

  public Future<List<R>> under(String parent) {
    return backend.under(parent).map(rows -> rows.stream().map(this::resource).toList());
  }

  Future<Boolean> occupies(String parent) {
    return backend.occupies(parent);
  }

  Future<Set<String>> beneath(Collection<String> parents) {
    return backend.beneath(parents);
  }

  Future<Page<R>> page(Filter scope, boolean showDeleted, PageRequest request) {
    return backend.page(scope, showDeleted, request).map(page -> new Page<>(page.items().stream()
        .map(this::resource).toList(), page.nextPageToken(), page.totalSize()));
  }

  @Override
  public Future<Page<R>> query(Subject subject, String permission, PageRequest request, boolean showDeleted,
      Struct context) {
    relation(permission);
    return new ResourceQuery<>(this, subject, permission, context).page(request, showDeleted);
  }

  @Override
  public Future<Optional<Resource>> place(String parent) {
    if (declared.parents().isEmpty()) {
      if (!parent.isEmpty()) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + name() + " has no parents, but the"
            + " request names " + parent + ".");
      }
      return Future.succeededFuture(Optional.empty());
    }
    if (parent.isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A resource of the type " + name() + " is under one of "
          + declared.parents() + ", but the request names no parent.");
    }
    return schema.require(parent).map(found -> {
      if (!declared.parents().contains(found.type().name())) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A resource of the type " + name() + " cannot be"
            + " under " + parent + ".");
      }
      found.requireActive();
      return Optional.of(found);
    });
  }

  @Override
  public Future<R> create(Subject creator, String parent, String id, Map<String, Object> values,
      Struct context) {
    if (!ID.matcher(id).matches()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The id " + id + " does not match " + ID.pattern()
          + ".");
    }
    for (String field : values.keySet()) {
      if (owned.contains(field)) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The field " + field + " is Pyrite's; a request"
            + " cannot set it.");
      }
    }
    return place(parent)
        .compose(found -> found.isEmpty() ? Future.succeededFuture(found)
            : found.get().require(creator, permissions().getCreate(), context).map(found))
        .compose(found -> insert(found, id, values))
        .compose(created -> declared.creator().isEmpty() ? Future.succeededFuture(created)
            : schema.adapter().write(TupleKey.newBuilder().setUser(creator.toString())
                .setRelation(declared.creator()).setObject(created.object()).build()).map(created));
  }

  protected Future<R> insert(Optional<Resource> parent, String id, Map<String, Object> values) {
    String name = declared.resource(id);
    Instant now = schema.clock().instant();
    Map<String, Object> row = new HashMap<>();
    declared.fields().keySet().forEach(field -> row.put(field, null));
    row.putAll(values);
    row.put(Resource.NAME, name);
    row.put(Resource.UID, UUID.randomUUID().toString());
    row.put(Resource.PARENT, parent.map(Resource::name).orElse(null));
    row.computeIfAbsent(Resource.LABELS, ignored -> new JsonObject());
    row.put(Resource.REVISION, 1L);
    row.put(Resource.CREATE_TIME, now);
    row.put(Resource.UPDATE_TIME, now);
    Row created = new Row(row);
    return backend.taken(name)
        .compose(taken -> taken
            ? Future.failedFuture(new PyriteException(GrpcStatus.ALREADY_EXISTS, "The resource " + name
                + " already exists."))
            : backend.insert().values(created).execute())
        .map(ignored -> resource(created));
  }

  Future<R> change(Resource resource, Map<String, Object> changes) {
    return backend.change(resource.uid(), resource.revision(), changes, schema.clock().instant())
        .map(changed -> changed.map(this::resource).orElseThrow(() -> new PyriteException(GrpcStatus.ABORTED,
            "The resource " + resource.name() + " changed since revision " + resource.revision() + ".")));
  }

  Future<Long> purge(Instant now) {
    return backend.due(now).compose(rows -> {
      Future<Long> purged = Future.succeededFuture(0L);
      for (Row row : rows) {
        purged = purged.compose(count -> resource(row).purge().map(count + 1));
      }
      return purged;
    });
  }

  Future<Void> remove(Resource resource) {
    return backend.remove(resource.uid());
  }

  Future<Void> removeBeneath(String parent) {
    return backend.removeBeneath(parent);
  }
}
