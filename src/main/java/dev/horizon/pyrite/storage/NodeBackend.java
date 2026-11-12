package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.filter.Filter.eq;

import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.schema.declaration.v1.Tables;
import io.vertx.core.Future;

/**
 * The rows of the resources that Pyrite knows, in the table {@code pyrite_node}, by name: the type of each, and the
 * resource that it is under. The database refuses to delete the row of a resource that rows are under, and deletes
 * the rows of a key or an account with the row of its resource.
 *
 * @param database the database of Pyrite's tables
 */
public record NodeBackend(Database database) implements Backend<Tables.Node, String> {

  @Override
  public Tables.Node prototype() {
    return Tables.Node.getDefaultInstance();
  }

  /**
   * Tells whether resources are under a resource.
   *
   * @param parent the name of the resource, such as {@code organizations/acme}
   * @return whether a row names it as its parent
   */
  public Future<Boolean> occupied(String parent) {
    return exists(query().where(eq("parent", parent)));
  }
}
