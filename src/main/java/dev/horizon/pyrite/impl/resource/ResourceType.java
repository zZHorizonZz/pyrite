package dev.horizon.pyrite.impl.resource;

import com.google.protobuf.Struct;
import dev.horizon.aquery.aip158.Page;
import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.model.Definition;
import dev.horizon.pyrite.schema.v1.Permissions;
import io.vertx.core.Future;
import java.util.Map;
import java.util.Optional;

/**
 * A declared type of resource, which reads, creates and queries the resources in its table.
 *
 * <p>
 * Pyrite's own types extend it, such as {@code KeyType} and {@code AccountType}. They add what their resources need,
 * and refuse what a request must not do, such as creating a session.
 *
 * @param <R> the resources of the type
 */
public interface ResourceType<R extends Resource> {

  /**
   * Gives the type's name.
   *
   * @return the name, such as {@code acme.dev/Project}
   */
  String name();

  /**
   * Gives the type as the declaration declares it.
   *
   * @return the declared type
   */
  DeclaredType declaration();

  /**
   * Gives the relations that the methods of the data and access services check.
   *
   * @return the permissions
   */
  Permissions permissions();

  /**
   * Gives the schema that the type belongs to.
   *
   * @return the schema
   */
  Schema schema();

  /**
   * Gives a relation of the type.
   *
   * @param relation the relation's name
   * @return the relation, or a failure with {@code INVALID_ARGUMENT} when the type does not declare it
   */
  Definition relation(String relation);

  /**
   * Converts a field's {@code pyrite.data.v1.Value} to the value that the table holds.
   *
   * @param field the field's name
   * @param value the value
   * @return the value, or a failure with {@code INVALID_ARGUMENT} for an unknown field or a value of another kind
   */
  Object parse(String field, Value value);

  /**
   * Reads a resource of the type.
   *
   * @param name the resource's name
   * @return the resource, or empty when the table has no row of the name
   */
  Future<Optional<R>> get(String name);

  /**
   * Creates a resource of the type. The creator needs the type's {@code create} permission on the parent, and is
   * granted the type's {@code creator} relation on the new resource.
   *
   * @param creator the subject that creates it
   * @param parent the parent's name; empty for a type without parents
   * @param id the resource's id, the last segment of its name
   * @param values the values of the fields that a request may set
   * @param context the values that the conditions of grants read
   * @return the resource
   */
  Future<R> create(Subject creator, String parent, String id, Map<String, Object> values, Struct context);

  /**
   * Lists a page of the resources of the type on which a subject has a permission. Pyrite finds where the permission
   * can come from, reads the rows beneath, and checks each row of the page.
   *
   * @param subject the subject
   * @param permission the permission
   * @param request the filter, the order and the page
   * @param showDeleted whether deleted resources are listed too
   * @param context the values that the conditions of grants read
   * @return the page, which can hold fewer resources than its size
   */
  Future<Page<R>> query(Subject subject, String permission, PageRequest request, boolean showDeleted,
      Struct context);

  /**
   * Reads the parent that a resource of the type would have, and checks that its type is a parent type.
   *
   * @param parent the parent's name; empty for a type without parents
   * @return the parent, or empty for a type without parents
   */
  Future<Optional<Resource>> place(String parent);
}
