package dev.horizon.pyrite.impl.storage;

import static dev.horizon.basepoint.Filter.and;
import static dev.horizon.basepoint.Filter.eq;

import dev.horizon.cinder.Backend;
import dev.horizon.pyrite.PyriteException;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Optional;

/**
 * The rows of one of Pyrite's own resources, such as keys or accounts: each row has a uid, a unique name, the name of
 * its parent, and a revision that every change raises.
 *
 * @param <E> the row's message
 */
public interface ResourceBackend<E> extends Backend<E, String> {

  /**
   * Reads the row of a name.
   *
   * @param name the name, such as {@code keys/payments}
   * @return the row, or empty when no row has the name
   */
  default Future<Optional<E>> findByName(String name) {
    return find(eq("name", name)).first();
  }

  /**
   * Reads the children of a resource.
   *
   * @param parent the parent's name
   * @return the children, by name
   */
  default Future<List<E>> children(String parent) {
    return find(eq("parent", parent)).orderBy("name").list();
  }

  /**
   * Writes a changed row over the row of a uid, if the row is still at a revision.
   *
   * @param changed the row with its changes and the next revision
   * @param uid the row's uid
   * @param revision the revision that the change was made from
   * @param name the row's name, for the error
   * @return the changed row, or a failure with {@code ABORTED} when another change came first
   */
  default Future<E> update(E changed, String uid, long revision, String name) {
    return update().where(and(eq("uid", uid), eq("revision", revision))).set(changed, "*").returning()
        .map(updated -> updated.stream().findFirst().orElseThrow(() -> new PyriteException(GrpcStatus.ABORTED,
            "The resource " + name + " changed since revision " + revision + ".")));
  }

  /**
   * Deletes the children of a resource.
   *
   * @param parent the parent's name
   * @return a future that completes once they are deleted
   */
  default Future<Void> deleteChildren(String parent) {
    return delete().where(eq("parent", parent)).execute().mapEmpty();
  }
}
