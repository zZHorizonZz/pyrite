package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.schema.v1.Field;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A resource of a declared type: a row of the type's table, which checks, changes, grants on and purges itself.
 *
 * <p>
 * A resource is read once per call and does not follow later changes. Each change names the revision that the caller
 * read, and fails with {@code ABORTED} when the row is at another one.
 */
public interface Resource {

  String NAME = "name";
  String UID = "uid";
  String PARENT = "parent";
  String DISPLAY_NAME = "display_name";
  String LABELS = "labels";
  String REVISION = "revision";
  String CREATE_TIME = "create_time";
  String UPDATE_TIME = "update_time";
  String DELETE_TIME = "delete_time";
  String PURGE_TIME = "purge_time";

  /**
   * The standard fields, which every type's table has.
   */
  List<Field> STANDARD = List.of(
      Field.newBuilder().setName(NAME).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(UID).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(PARENT).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(DISPLAY_NAME).setKind(Field.Kind.STRING).build(),
      Field.newBuilder().setName(LABELS).setKind(Field.Kind.MAP).build(),
      Field.newBuilder().setName(REVISION).setKind(Field.Kind.INTEGER).build(),
      Field.newBuilder().setName(CREATE_TIME).setKind(Field.Kind.TIMESTAMP).build(),
      Field.newBuilder().setName(UPDATE_TIME).setKind(Field.Kind.TIMESTAMP).build(),
      Field.newBuilder().setName(DELETE_TIME).setKind(Field.Kind.TIMESTAMP).build(),
      Field.newBuilder().setName(PURGE_TIME).setKind(Field.Kind.TIMESTAMP).build());

  /**
   * Gives the resource's type.
   *
   * @return the type
   */
  ResourceType<?> type();

  /**
   * Gives the resource's name.
   *
   * @return the name, such as {@code projects/ledger}
   */
  String name();

  /**
   * Gives the resource's uid, a UUID that Pyrite generates at creation.
   *
   * @return the uid
   */
  String uid();

  /**
   * Gives the name of the resource's parent.
   *
   * @return the parent's name; empty for a resource of a type without parents
   */
  Optional<String> parentName();

  /**
   * Gives the resource's revision: {@code 1} at creation, one more at each change.
   *
   * @return the revision
   */
  long revision();

  /**
   * Gives the time of the resource's last change.
   *
   * @return the time
   */
  Instant updateTime();

  /**
   * Gives the time after which a deleted resource can be purged.
   *
   * @return the time; empty for an active resource
   */
  Optional<Instant> purgeTime();

  /**
   * Gives the last segment of the resource's name.
   *
   * @return the id, such as {@code ledger}
   */
  String id();

  /**
   * Gives the resource as an OpenFGA object.
   *
   * @return the object, such as {@code project:ledger}
   */
  String object();

  /**
   * Tells whether the resource is deleted and waits for its purge.
   *
   * @return whether it is deleted
   */
  boolean isDeleted();

  /**
   * Checks that the resource is active.
   *
   * @return the resource, or a failure with {@code FAILED_PRECONDITION} when it is deleted
   */
  Resource requireActive();

  /**
   * Checks that the resource is at a revision.
   *
   * @param expected the revision that the caller read
   * @return the resource, or a failure with {@code ABORTED} when it is at another revision
   */
  Resource requireRevision(long expected);

  /**
   * Reads the resource's parent.
   *
   * @return the parent; empty for a resource of a type without parents
   */
  Future<Optional<Resource>> parent();

  /**
   * Reads the resource's ancestors, from its parent up.
   *
   * @return the ancestors, or a failure with {@code FAILED_PRECONDITION} when there are more than {@code maxDepth}
   */
  Future<List<Resource>> ancestors();

  /**
   * Checks whether a principal has a relation on the resource. OpenFGA answers the parts of the relation on the
   * resource itself, and Pyrite walks the parents for the parts that read from the parent.
   *
   * @param principal the principal, such as {@code user:anne}
   * @param relation the relation
   * @param context the values of the conditions' parameters
   * @return whether the principal has the relation
   */
  Future<Boolean> check(String principal, String relation, Struct context);

  /**
   * Checks that a subject has a relation on the resource.
   *
   * @param subject the subject
   * @param relation the relation
   * @param context the values that the conditions of grants read
   * @return the resource, or a failure with {@code PERMISSION_DENIED}
   */
  Future<Resource> require(Subject subject, String relation, Struct context);

  /**
   * Changes the fields of the resource that a request may change.
   *
   * @param changes the new values, by field; {@code null} unsets a field
   * @param revision the revision that the caller read
   * @return the changed resource, at the next revision
   */
  Future<Resource> update(Map<String, Object> changes, long revision);

  /**
   * Deletes the resource, which stays in its table until its type's retention ends. A resource with active resources
   * beneath it is not deleted.
   *
   * @param revision the revision that the caller read
   * @return the deleted resource, with its {@code delete_time} and {@code purge_time}
   */
  Future<Resource> delete(long revision);

  /**
   * Makes a deleted resource active again, before its retention ends.
   *
   * @param revision the revision that the caller read
   * @return the active resource
   */
  Future<Resource> undelete(long revision);

  /**
   * Moves the resource under another parent, which is not beneath it. Its grants stay, and what it inherits changes at
   * once.
   *
   * @param parent the new parent
   * @param revision the revision that the caller read
   * @return the moved resource
   */
  Future<Resource> move(Resource parent, long revision);

  /**
   * Grants a relation on the resource. Granting it again changes nothing.
   *
   * @param grant the relation, which the type lists as grantable, the grantee and the condition
   * @param context the values that the conditions of grants read, while the cycle check walks them
   * @return when granted, or a failure with {@code FAILED_PRECONDITION} for a grant that makes a cycle
   */
  Future<Void> grant(Grant grant, Struct context);

  /**
   * Revokes a grant on the resource. Revoking one that does not exist changes nothing.
   *
   * @param grant the relation and the grantee
   * @return when revoked
   */
  Future<Void> revoke(Grant grant);

  /**
   * Lists a page of the grants on the resource.
   *
   * @param relation the relation; empty for every one
   * @param grantee the grantee; empty for every one
   * @param pageSize the most grants
   * @param token the page token; empty for the first page
   * @return the page
   */
  Future<Page<Grant>> grants(String relation, String grantee, int pageSize, String token);

  /**
   * Lists the principals of a type that have a permission on the resource, through its grants and its ancestors'.
   *
   * @param permission the permission
   * @param filter the type of the principals, and their relation for a set
   * @param context the values of the conditions' parameters
   * @return the principals
   */
  Future<List<String>> subjects(String permission, UserTypeFilter filter, Struct context);

  /**
   * Removes the resource's row, Pyrite's resources beneath it, and every grant on it and to it.
   *
   * @return when removed
   */
  Future<Void> purge();

  /**
   * Gives the resource as a {@code pyrite.data.v1.Resource}, without the fields that Pyrite never returns.
   *
   * @return the message
   */
  dev.horizon.pyrite.data.v1.Resource toMessage();
}
