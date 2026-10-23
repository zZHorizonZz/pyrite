package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.impl.storage.Row;
import dev.horizon.spectrum.openfga.v1.ReadRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class BaseResource implements Resource {

  private final BaseResourceType<?> type;
  private final Row row;

  protected BaseResource(BaseResourceType<?> type, Row row) {
    this.type = type;
    this.row = row;
  }

  @Override
  public ResourceType<?> type() {
    return type;
  }

  @Override
  public String name() {
    return row.string(NAME);
  }

  @Override
  public String uid() {
    return row.string(UID);
  }

  @Override
  public String parentName() {
    return row.string(PARENT);
  }

  @Override
  public long revision() {
    return (Long) row.get(REVISION);
  }

  @Override
  public Instant updateTime() {
    return (Instant) row.get(UPDATE_TIME);
  }

  @Override
  public Optional<Instant> purgeTime() {
    return Optional.ofNullable((Instant) row.get(PURGE_TIME));
  }

  @Override
  public String id() {
    return name().substring(type.declaration().plural().length() + 1);
  }

  @Override
  public String object() {
    return type.declaration().object(id());
  }

  @Override
  public boolean isDeleted() {
    return row.get(DELETE_TIME) != null;
  }

  protected Row row() {
    return row;
  }

  @SuppressWarnings("unchecked")
  protected <T> T field(String field) {
    return (T) row.get(field);
  }

  @Override
  public Resource requireActive() {
    if (isDeleted()) {
      throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The resource " + name() + " is deleted.");
    }
    return this;
  }

  @Override
  public Resource requireRevision(long expected) {
    if (revision() != expected) {
      throw new PyriteException(GrpcStatus.ABORTED, "The resource " + name() + " is at revision " + revision()
          + ", not " + expected + ".");
    }
    return this;
  }

  @Override
  public Future<Optional<Resource>> parent() {
    return parentName().isEmpty() ? Future.succeededFuture(Optional.empty()) : type.schema().get(parentName());
  }

  @Override
  public Future<List<Resource>> ancestors() {
    List<Resource> ancestors = new ArrayList<>();
    return ancestors(this, ancestors).map(ancestors);
  }

  @Override
  public Future<Boolean> check(String principal, String relation, Struct context) {
    return new Check(principal, context).on(this, relation);
  }

  @Override
  public Future<Resource> require(Subject subject, String relation) {
    return check(subject.toString(), relation, Struct.getDefaultInstance())
        .compose(allowed -> allowed ? Future.succeededFuture(this)
            : Future.failedFuture(new PyriteException(GrpcStatus.PERMISSION_DENIED, "The subject " + subject
                + " has no " + relation + " on " + name() + ".")));
  }

  @Override
  public Future<Resource> update(Map<String, Object> changes, long revision) {
    return change(checkUpdate(changes, revision));
  }

  @Override
  public Future<Resource> delete(long revision) {
    requireActive().requireRevision(revision);
    Future<Boolean> occupied = Future.succeededFuture(false);
    for (BaseResourceType<?> child : type.schema().children(type)) {
      if (!child.dependent()) {
        occupied = occupied.compose(any -> any ? Future.succeededFuture(true) : child.occupies(name()));
      }
    }
    return occupied.compose(any -> {
      if (any) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The resource " + name() + " has resources"
            + " beneath it that are not deleted.");
      }
      Instant now = type.schema().clock().instant();
      return change(Map.of(DELETE_TIME, now, PURGE_TIME, now.plus(type.declaration().retention())));
    });
  }

  @Override
  public Future<Resource> undelete(long revision) {
    if (!isDeleted()) {
      throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The resource " + name() + " is not deleted.");
    }
    requireRevision(revision);
    if (!purgeTime().orElseThrow().isAfter(type.schema().clock().instant())) {
      throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The retention of " + name() + " has ended.");
    }
    Map<String, Object> changes = new HashMap<>();
    changes.put(DELETE_TIME, null);
    changes.put(PURGE_TIME, null);
    return change(changes);
  }

  @Override
  public Future<Resource> move(Resource parent, long revision) {
    requireActive().requireRevision(revision);
    return parent.ancestors().compose(ancestors -> {
      if (parent.name().equals(name()) || ancestors.stream().anyMatch(ancestor -> ancestor.name().equals(name()))) {
        throw new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The resource " + name() + " cannot move under "
            + parent.name() + ", which is beneath it.");
      }
      return change(Map.of(PARENT, parent.name()));
    });
  }

  @Override
  public Future<Void> grant(Grant grant) {
    grantable(grant);
    TupleKey.Builder tuple = TupleKey.newBuilder().setUser(grant.getGrantee()).setRelation(grant.getRelation())
        .setObject(object());
    if (grant.hasCondition()) {
      tuple.setCondition(RelationshipCondition.newBuilder().setName(grant.getCondition().getName())
          .setContext(grant.getCondition().getContext()));
    }
    return acyclic(grant).compose(ignored -> type.schema().adapter().write(tuple.build()));
  }

  @Override
  public Future<Void> revoke(Grant grant) {
    grantable(grant);
    return type.schema().adapter().delete(List.of(TupleKeyWithoutCondition.newBuilder().setUser(grant.getGrantee())
        .setRelation(grant.getRelation()).setObject(object()).build()));
  }

  @Override
  public Future<Page<Grant>> grants(String relation, String grantee, int pageSize, String token) {
    return type.schema().adapter().read(ReadRequestTupleKey.newBuilder().setObject(object()).setRelation(relation)
        .setUser(grantee).build(), pageSize, token)
        .map(read -> new Page<>(read.getTuplesList().stream()
            .flatMap(tuple -> type.schema().grant(tuple.getKey()).stream()).toList(), read.getContinuationToken(), 0));
  }

  @Override
  public Future<List<String>> subjects(String permission, UserTypeFilter filter, Struct context) {
    return new SubjectQuery(this, permission, filter, context).subjects();
  }

  @Override
  public Future<Void> purge() {
    Future<Void> dependents = Future.succeededFuture();
    for (BaseResourceType<?> child : type.schema().children(type)) {
      if (child.dependent()) {
        dependents = dependents.compose(ignored -> child.removeBeneath(name()));
      }
    }
    return dependents.compose(ignored -> forget()).compose(ignored -> type.remove(this));
  }

  @Override
  public dev.horizon.pyrite.data.v1.Resource toMessage() {
    dev.horizon.pyrite.data.v1.Resource.Builder message = dev.horizon.pyrite.data.v1.Resource.newBuilder()
        .setName(name()).setType(type.name());
    row.values().forEach((field, value) -> {
      if (!type.secretFields().contains(field)) {
        message.putFields(field, new FieldValue(value).message());
      }
    });
    return message.build();
  }

  protected Map<String, Object> checkUpdate(Map<String, Object> changes, long revision) {
    requireActive().requireRevision(revision);
    for (String field : changes.keySet()) {
      if (type.pyriteFields().contains(field) || type.constantFields().contains(field)) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The field " + field + " is set by Pyrite or when the"
            + " resource is created; an update cannot set it.");
      }
    }
    return changes;
  }

  protected Future<Resource> change(Map<String, Object> changes) {
    return type.change(this, changes).map(changed -> changed);
  }

  private void grantable(Grant grant) {
    if (grant.getGrantee().isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The grant names no grantee.");
    }
    if (!type.declaration().grantable().contains(grant.getRelation())) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name() + " does not grant "
          + grant.getRelation() + ".");
    }
  }

  private Future<Void> acyclic(Grant grant) {
    int hash = grant.getGrantee().indexOf('#');
    if (hash < 0 || type.schema().types().readsFromParent(type.declaration(), grant.getRelation())) {
      return Future.succeededFuture();
    }
    String set = grant.getGrantee().substring(0, hash);
    if (set.equals(object())) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Granting "
          + grant.getRelation() + " on " + name() + " to its own " + grant.getGrantee() + " makes a cycle."));
    }
    return type.schema().adapter().check(object() + "#" + grant.getRelation(),
        grant.getGrantee().substring(hash + 1), set, Struct.getDefaultInstance())
        .compose(within -> within
            ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Granting "
                + grant.getRelation() + " on " + name() + " to " + grant.getGrantee() + " makes a cycle."))
            : Future.succeededFuture());
  }

  private Future<Void> forget() {
    List<ReadRequestTupleKey> keys = new ArrayList<>();
    keys.add(ReadRequestTupleKey.newBuilder().setObject(object()).build());
    for (BaseResourceType<?> holder : type.schema().all()) {
      String objects = holder.declaration().singular() + ":";
      keys.add(ReadRequestTupleKey.newBuilder().setObject(objects).setUser(object()).build());
      for (String relation : type.declaration().relations().keySet()) {
        keys.add(ReadRequestTupleKey.newBuilder().setObject(objects).setUser(object() + "#" + relation).build());
      }
    }
    Future<Void> done = Future.succeededFuture();
    for (ReadRequestTupleKey key : keys) {
      done = done.compose(ignored -> type.schema().adapter().forget(key));
    }
    return done;
  }

  private Future<Void> ancestors(Resource from, List<Resource> ancestors) {
    int limit = type.schema().options().maxDepth();
    if (ancestors.size() >= limit) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The resource " + name()
          + " has more than " + limit + " ancestors."));
    }
    return from.parent().compose(parent -> {
      if (parent.isEmpty()) {
        return Future.succeededFuture();
      }
      ancestors.add(parent.get());
      return ancestors(parent.get(), ancestors);
    });
  }
}
