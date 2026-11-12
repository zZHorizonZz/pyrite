package dev.horizon.pyrite.resource;

import com.google.protobuf.Struct;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.OpenFga;
import dev.horizon.pyrite.access.v1.Condition;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import dev.horizon.pyrite.storage.NodeBackend;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The tree of resources: each a node of the database under its parent, and an object of OpenFGA whose relation
 * {@code parent} names the parent's object. It makes resources known, finds them and forgets them.
 */
public final class ResourceTree {

  private static final Pattern ID = Pattern.compile("[a-z]([a-z0-9-]{0,61}[a-z0-9])?");

  private final TypeSystem types;
  private final OpenFga openFga;
  private final NodeBackend nodes;

  public ResourceTree(TypeSystem types, OpenFga openFga, Database database) {
    this.types = types;
    this.openFga = openFga;
    this.nodes = new NodeBackend(database);
  }

  public TypeSystem types() {
    return types;
  }

  public OpenFga openFga() {
    return openFga;
  }

  /**
   * Makes a resource known under a parent, which the creator needs the type's create permission on, and grants the
   * creator the relation that the type grants creators. It records the node, then writes the {@code parent} tuple and
   * the grant of the creator to OpenFGA.
   *
   * @param creator the subject that makes the resource known
   * @param name the name, such as {@code projects/ledger}
   * @param parent the parent's name; empty for a type without parents
   * @param context the values that the conditions of grants read, for the check of the create permission
   * @return the resource, or a failure with {@code INVALID_ARGUMENT} for a name of no declared type, an id that does
   * not match, or a missing or wrong parent, with {@code NOT_FOUND} for a parent that is not known, with
   * {@code PERMISSION_DENIED} without the create permission, and with {@code ALREADY_EXISTS} when the name is known
   */
  public Future<Resource> create(Subject creator, String name, String parent, Struct context) {
    Optional<ResourceType> declared = types.typeOf(name);
    if (declared.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The name " + name + " is not of"
          + " a declared type: its first segment is no type's plural."));
    }
    ResourceType type = declared.get();
    if (!ID.matcher(type.id(name)).matches()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The id of " + name + " does not"
          + " match " + ID.pattern() + "."));
    }
    Resource created = new Resource(this, type, name);
    return place(type, parent)
        .compose(above -> above.isEmpty() ? Future.succeededFuture(above)
            : above.get().require(creator, type.permissions().getCreate(), context).map(above))
        .compose(above -> nodes.existsById(name).compose(known -> {
          if (known) {
            return Future.failedFuture(new PyriteException(GrpcStatus.ALREADY_EXISTS, "The resource " + name
                + " is known already."));
          }
          Tables.Node.Builder node = Tables.Node.newBuilder().setName(name).setType(type.name());
          above.ifPresent(found -> node.setParent(found.name()));
          return nodes.insert(node.build()).map(above);
        }))
        .compose(above -> above.isEmpty() ? Future.succeededFuture()
            : openFga.write(TupleKey.newBuilder().setUser(above.get().object().toString())
                .setRelation(TypeSystem.PARENT).setObject(created.object().toString()).build()))
        .compose(ignored -> type.creator().isEmpty() ? Future.succeededFuture()
            : openFga.write(TupleKey.newBuilder().setUser(creator.toString()).setRelation(type.creator())
                .setObject(created.object().toString()).build()))
        .map(created);
  }

  /**
   * Forgets a resource that has none under it: its tuples in OpenFGA, and its node, with which the database deletes
   * the rows of the resource, such as those of a key and its versions.
   *
   * @param name the name of the resource
   * @return a future that completes once the resource is forgotten, or a failure with {@code NOT_FOUND} when it is not
   * known, and with {@code FAILED_PRECONDITION} when resources are under it
   */
  public Future<Void> forget(String name) {
    return require(name).compose(found -> nodes.occupied(name).compose(occupied -> occupied
        ? Future.failedFuture(new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The resource " + name + " has"
            + " resources under it; forget them first."))
        : found.forget())).compose(ignored -> nodes.deleteById(name)).mapEmpty();
  }

  /**
   * Finds a resource.
   *
   * @param name the name of the resource, such as {@code projects/ledger}
   * @return the resource, or empty when the name is of no declared type, or not known
   */
  public Future<Optional<Resource>> get(String name) {
    return types.typeOf(name)
        .map(type -> nodes.findById(name).map(node -> node.filter(found -> found.getType().equals(type.name()))
            .map(found -> new Resource(this, type, name))))
        .orElseGet(() -> Future.succeededFuture(Optional.empty()));
  }

  /**
   * Finds a resource that is known.
   *
   * @param name the name of the resource, such as {@code projects/ledger}
   * @return the resource, or a failure with {@code NOT_FOUND} when the name is of no declared type, or not known
   */
  public Future<Resource> require(String name) {
    return get(name).compose(found -> found.isPresent() ? Future.succeededFuture(found.get())
        : Future.failedFuture(new PyriteException(GrpcStatus.NOT_FOUND, "The resource " + name + " is not known.")));
  }

  /**
   * Gives the grant that a tuple holds.
   *
   * @param tuple the tuple, such as {@code user:bob viewer project:ledger}
   * @return the grant on the resource of the object, such as {@code projects/ledger}, or empty for a tuple on an object
   * of no declared type
   */
  public Optional<Grant> grant(TupleKey tuple) {
    return ObjectRef.parse(tuple.getObject()).flatMap(object -> types.singular(object.type()).map(type -> {
      Grant.Builder grant = Grant.newBuilder().setObject(type.resource(object.id())).setRelation(tuple.getRelation())
          .setGrantee(tuple.getUser());
      if (tuple.hasCondition()) {
        grant.setCondition(Condition.newBuilder().setName(tuple.getCondition().getName())
            .setContext(tuple.getCondition().getContext()));
      }
      return grant.build();
    }));
  }

  /**
   * Finds the parent of a new resource of a type, which a type with parents needs, and a type without refuses.
   */
  private Future<Optional<Resource>> place(ResourceType type, String parent) {
    if (type.parents().isEmpty()) {
      return parent.isEmpty() ? Future.succeededFuture(Optional.empty())
          : Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name()
              + " has no parents, but the request names " + parent + "."));
    }
    if (parent.isEmpty()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A resource of the type "
          + type.name() + " is under one of " + type.parents() + ", but the request names no parent."));
    }
    return require(parent).compose(found -> type.parents().contains(found.type().name())
        ? Future.succeededFuture(Optional.of(found))
        : Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A resource of the type "
            + type.name() + " cannot be under " + parent + ".")));
  }
}
