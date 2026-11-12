package dev.horizon.pyrite.resource;

import com.google.protobuf.Struct;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.declaration.TypeSystem;
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
import java.util.Optional;

/**
 * A known resource of a declared type, such as {@code projects/ledger}: a node of the tree, and the object
 * {@code project:ledger} of OpenFGA, which grants and checks reach.
 *
 * <pre>{@code
 * tree.require("projects/ledger")
 *     .compose(project -> project.require(subject, project.type().permissions().getGrant(), context))
 *     .compose(project -> project.grant(grant, context));
 * }</pre>
 *
 * @param tree the tree that knows the resource
 * @param type the type of the resource
 * @param name the name of the resource, such as {@code projects/ledger}
 */
public record Resource(ResourceTree tree, ResourceType type, String name) {

  /**
   * Gives the id of the resource, the last segment of its name.
   *
   * @return the id, such as {@code ledger}
   */
  public String id() {
    return type.id(name);
  }

  /**
   * Gives the object of the resource in OpenFGA.
   *
   * @return the object, such as {@code project:ledger}
   */
  public ObjectRef object() {
    return new ObjectRef(type.singular(), id());
  }

  /**
   * Gives the resource, if its type has a relation.
   *
   * @param relation the relation, such as {@code can_view}
   * @return the resource, or a failure with {@code INVALID_ARGUMENT} when the type does not declare the relation
   */
  public Future<Resource> requireRelation(String relation) {
    return type.relations().containsKey(relation) ? Future.succeededFuture(this)
        : Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name() + " has no"
            + " relation " + relation + "."));
  }

  /**
   * Asks OpenFGA whether a principal has a relation on the resource.
   *
   * @param principal the principal, such as {@code user:bob}, or a set, such as {@code project:ledger#editor}
   * @param relation the relation, such as {@code can_view}
   * @param context the values that the conditions of grants read, such as {@code current_time}
   * @return whether the principal has the relation, or the failure of OpenFGA, such as a condition that misses a value
   */
  public Future<Boolean> check(String principal, String relation, Struct context) {
    return tree.openFga().check(principal, relation, object().toString(), context);
  }

  /**
   * Gives the resource, if a subject has a relation on it.
   *
   * @param subject the subject of the call
   * @param relation the relation that the call needs, such as the type's grant permission
   * @param context the values that the conditions of grants read
   * @return the resource, or a failure with {@code PERMISSION_DENIED} that names the subject, the relation and the
   * resource
   */
  public Future<Resource> require(Subject subject, String relation, Struct context) {
    return check(subject.toString(), relation, context)
        .compose(allowed -> allowed ? Future.succeededFuture(this)
            : Future.failedFuture(new PyriteException(GrpcStatus.PERMISSION_DENIED, "The subject " + subject
                + " has no " + relation + " on " + name + ".")));
  }

  /**
   * Grants a relation of the type to a grantee, unless the grant makes a cycle of sets. Granting it again changes
   * nothing.
   *
   * @param grant the grant, whose object is this resource, with its condition, if any
   * @param context the values that the conditions of grants read, for the check of a cycle
   * @return a future that completes once OpenFGA holds the grant, or a failure with {@code INVALID_ARGUMENT} for a
   * relation that the type does not grant or a grantee of no form, with {@code FAILED_PRECONDITION} for a cycle, or
   * with the status of OpenFGA for a grantee or a condition that the relation does not take
   */
  public Future<Void> grant(Grant grant, Struct context) {
    return grantee(grant).compose(grantee -> {
      TupleKey.Builder tuple = TupleKey.newBuilder().setUser(grantee.toString()).setRelation(grant.getRelation())
          .setObject(object().toString());
      if (grant.hasCondition()) {
        tuple.setCondition(RelationshipCondition.newBuilder().setName(grant.getCondition().getName())
            .setContext(grant.getCondition().getContext()));
      }
      return acyclic(grant.getRelation(), grantee, context).compose(ignored -> tree.openFga().write(tuple.build()));
    });
  }

  /**
   * Revokes a grant. Revoking one that does not exist changes nothing.
   *
   * @param grant the grant, whose object is this resource
   * @return a future that completes once OpenFGA no longer holds the grant, or a failure with
   * {@code INVALID_ARGUMENT} for a relation that the type does not grant or a grantee of no form
   */
  public Future<Void> revoke(Grant grant) {
    return grantee(grant).compose(grantee -> tree.openFga().delete(List.of(TupleKeyWithoutCondition.newBuilder()
        .setUser(grantee.toString()).setRelation(grant.getRelation()).setObject(object().toString()).build())));
  }

  /**
   * Reads a page of the grants on the resource, without its {@code parent} tuple.
   *
   * @param relation only the grants of this relation; empty for every relation
   * @param grantee only the grants to this grantee; empty for every grantee
   * @param pageSize the most tuples to read, which {@code tuplesPerRead} bounds
   * @param token the continuation token of OpenFGA from the page before; empty for the first page
   * @return the page, with the continuation token of the next page, empty after the last
   */
  public Future<Page<Grant>> grants(String relation, String grantee, int pageSize, String token) {
    return tree.openFga().read(ReadRequestTupleKey.newBuilder().setObject(object().toString()).setRelation(relation)
        .setUser(grantee).build(), pageSize, token)
        .map(read -> new Page<>(read.getTuplesList().stream()
            .filter(tuple -> !tuple.getKey().getRelation().equals(TypeSystem.PARENT))
            .flatMap(tuple -> tree.grant(tuple.getKey()).stream()).toList(), read.getContinuationToken(), 0));
  }

  /**
   * Lists the principals that have a permission on the resource.
   *
   * @param permission the relation, such as {@code can_view}
   * @param filter the type of the principals, such as {@code user}, or of a set, such as {@code group#member}
   * @param context the values that the conditions of grants read
   * @return the principals, sorted, such as {@code user:anne}, or {@code user:*} for a grant to every user
   */
  public Future<List<String>> subjects(String permission, UserTypeFilter filter, Struct context) {
    return tree.openFga().users(type.singular(), id(), permission, filter, context)
        .map(users -> users.stream().map(Resource::text).filter(text -> !text.isEmpty()).sorted().toList());
  }

  /**
   * Deletes the tuples of the resource: those on it, and those that grant it, or a set of it, on a resource of any
   * declared type. It reads them {@code tuplesPerRead} at a time.
   *
   * @return a future that completes once OpenFGA holds none of them
   */
  public Future<Void> forget() {
    String object = object().toString();
    List<ReadRequestTupleKey> keys = new ArrayList<>();
    keys.add(ReadRequestTupleKey.newBuilder().setObject(object).build());
    for (ResourceType holder : tree.types().types()) {
      String objects = holder.singular() + ":";
      keys.add(ReadRequestTupleKey.newBuilder().setObject(objects).setUser(object).build());
      for (String relation : type.relations().keySet()) {
        keys.add(ReadRequestTupleKey.newBuilder().setObject(objects).setUser(object + "#" + relation).build());
      }
    }
    Future<Void> done = Future.succeededFuture();
    for (ReadRequestTupleKey key : keys) {
      done = done.compose(ignored -> tree.openFga().forget(key));
    }
    return done;
  }

  /**
   * Reads the grantee of a grant of a relation that the type grants.
   *
   * @return the grantee, or a failure with {@code INVALID_ARGUMENT}
   */
  private Future<Grantee> grantee(Grant grant) {
    if (!type.grantable().contains(grant.getRelation())) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name()
          + " does not grant " + grant.getRelation() + "."));
    }
    Optional<Grantee> grantee = Grantee.parse(grant.getGrantee());
    return grantee.isPresent() ? Future.succeededFuture(grantee.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The grantee '" + grant.getGrantee()
            + "' is not of the form type:id, type:* or type:id#relation."));
  }

  /**
   * Refuses a grant to a set that would contain the resource's own set of the relation.
   */
  private Future<Void> acyclic(String relation, Grantee grantee, Struct context) {
    if (!grantee.isUserset()) {
      return Future.succeededFuture();
    }
    if (grantee.object().equals(object())) {
      return Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Granting " + relation + " on "
          + name + " to its own " + grantee + " makes a cycle."));
    }
    return tree.openFga().check(object() + "#" + relation, grantee.relation(), grantee.object().toString(), context)
        .compose(within -> within
            ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "Granting " + relation + " on "
                + name + " to " + grantee + " makes a cycle."))
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
