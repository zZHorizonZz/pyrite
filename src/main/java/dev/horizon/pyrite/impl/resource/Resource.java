package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.Definition;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.spectrum.openfga.v1.ReadRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.RelationshipCondition;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.ArrayList;
import java.util.List;

public record Resource(Schema schema, DeclaredType type, String name) {

  public String id() {
    return type.id(name);
  }

  public String object() {
    return type.object(id());
  }

  public Definition relation(String relation) {
    Definition definition = type.relations().get(relation);
    if (definition == null) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name() + " has no relation "
          + relation + ".");
    }
    return definition;
  }

  public Future<Boolean> check(String principal, String relation, Struct context) {
    return schema.adapter().check(principal, relation, object(), context);
  }

  public Future<Resource> require(Subject subject, String relation, Struct context) {
    return check(subject.toString(), relation, context)
        .compose(allowed -> allowed ? Future.succeededFuture(this)
            : Future.failedFuture(new PyriteException(GrpcStatus.PERMISSION_DENIED, "The subject " + subject
                + " has no " + relation + " on " + name + ".")));
  }

  public Future<Void> grant(Grant grant, Struct context) {
    grantable(grant);
    TupleKey.Builder tuple = TupleKey.newBuilder().setUser(grant.getGrantee()).setRelation(grant.getRelation())
        .setObject(object());
    if (grant.hasCondition()) {
      tuple.setCondition(RelationshipCondition.newBuilder().setName(grant.getCondition().getName())
          .setContext(grant.getCondition().getContext()));
    }
    return acyclic(grant, context).compose(ignored -> schema.adapter().write(tuple.build()));
  }

  public Future<Void> revoke(Grant grant) {
    grantable(grant);
    return schema.adapter().delete(List.of(TupleKeyWithoutCondition.newBuilder().setUser(grant.getGrantee())
        .setRelation(grant.getRelation()).setObject(object()).build()));
  }

  public Future<Page<Grant>> grants(String relation, String grantee, int pageSize, String token) {
    return schema.adapter().read(ReadRequestTupleKey.newBuilder().setObject(object()).setRelation(relation)
        .setUser(grantee).build(), pageSize, token)
        .map(read -> new Page<>(read.getTuplesList().stream()
            .filter(tuple -> !tuple.getKey().getRelation().equals(TypeSystem.PARENT))
            .flatMap(tuple -> schema.grant(tuple.getKey()).stream()).toList(), read.getContinuationToken(), 0));
  }

  public Future<List<String>> subjects(String permission, UserTypeFilter filter, Struct context) {
    return schema.adapter().users(type.singular(), id(), permission, filter, context)
        .map(users -> users.stream().map(Resource::text).filter(text -> !text.isEmpty()).sorted().toList());
  }

  Future<Void> forget() {
    List<ReadRequestTupleKey> keys = new ArrayList<>();
    keys.add(ReadRequestTupleKey.newBuilder().setObject(object()).build());
    for (DeclaredType holder : schema.types().types()) {
      String objects = holder.singular() + ":";
      keys.add(ReadRequestTupleKey.newBuilder().setObject(objects).setUser(object()).build());
      for (String relation : type.relations().keySet()) {
        keys.add(ReadRequestTupleKey.newBuilder().setObject(objects).setUser(object() + "#" + relation).build());
      }
    }
    Future<Void> done = Future.succeededFuture();
    for (ReadRequestTupleKey key : keys) {
      done = done.compose(ignored -> schema.adapter().forget(key));
    }
    return done;
  }

  private void grantable(Grant grant) {
    if (grant.getGrantee().isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The grant names no grantee.");
    }
    if (!type.grantable().contains(grant.getRelation())) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name() + " does not grant "
          + grant.getRelation() + ".");
    }
  }

  private Future<Void> acyclic(Grant grant, Struct context) {
    int hash = grant.getGrantee().indexOf('#');
    if (hash < 0) {
      return Future.succeededFuture();
    }
    String set = grant.getGrantee().substring(0, hash);
    if (set.equals(object())) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Granting "
          + grant.getRelation() + " on " + name + " to its own " + grant.getGrantee() + " makes a cycle."));
    }
    return schema.adapter().check(object() + "#" + grant.getRelation(), grant.getGrantee().substring(hash + 1), set,
        context)
        .compose(within -> within
            ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Granting "
                + grant.getRelation() + " on " + name + " to " + grant.getGrantee() + " makes a cycle."))
            : Future.succeededFuture());
  }

  private static String text(User user) {
    return switch (user.getUserCase()) {
      case OBJECT -> user.getObject().getType() + ":" + user.getObject().getId();
      case USERSET -> user.getUserset().getType() + ":" + user.getUserset().getId() + "#"
          + user.getUserset().getRelation();
      case WILDCARD -> user.getWildcard().getType() + ":*";
      case USER_NOT_SET -> "";
    };
  }
}
