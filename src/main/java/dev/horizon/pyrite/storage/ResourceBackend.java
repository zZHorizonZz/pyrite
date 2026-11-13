package dev.horizon.pyrite.storage;

import static dev.horizon.basepoint.filter.Filter.and;
import static dev.horizon.basepoint.filter.Filter.eq;

import dev.horizon.basepoint.UpdateQuery;
import dev.horizon.basepoint.expression.Expression;
import dev.horizon.cinder.Backend;
import dev.horizon.cinder.Field;
import dev.horizon.pyrite.PyriteException;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The rows of one of Pyrite's own resources, such as keys or accounts: each row has a uid, a unique name, the name of
 * its parent, and a revision that every change raises. The database writes the uid, the revision and the times of the
 * creation and of each change.
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
    return first(query().where(eq("name", name)));
  }

  /**
   * Reads the children of a resource.
   *
   * @param parent the parent's name
   * @return the children, by name
   */
  default Future<List<E>> children(String parent) {
    return list(query().where(eq("parent", parent)).orderBy("name"));
  }

  /**
   * Writes a changed row over the row of a uid, if the row is still at a revision. The database raises the revision
   * and sets the time of the change.
   *
   * @param changed the row with its changes
   * @param uid the row's uid
   * @param revision the revision that the change was made from
   * @param name the row's name, for the error
   * @return the changed row, or a failure with {@code ABORTED} when another change came first
   */
  default Future<E> update(E changed, String uid, long revision, String name) {
    return update(changed, Map.of(), uid, revision, name);
  }

  /**
   * Inserts a row with some fields that the database computes, such as {@code expire_time} as
   * {@code add(now(), lifetime)}.
   *
   * @param set the expressions of the fields that the database computes, by field, as in the {@code SET} of an update
   * @return the row as the database holds it
   */
  default Future<E> insert(E row, Map<String, Expression> set) {
    return table().with(new ExpressionPart(set)).insert(row);
  }

  /**
   * Writes a changed row over the row of a uid, as {@link #update(Object, String, long, String)}, with some fields
   * that the database computes, such as {@code now()}.
   *
   * @param changed the row with its changes
   * @param set the expressions of the fields that the database computes, by field
   * @param uid the row's uid
   * @param revision the revision that the change was made from
   * @param name the row's name, for the error
   * @return the changed row, or a failure with {@code ABORTED} when another change came first
   */
  default Future<E> update(E changed, Map<String, Expression> set, String uid, long revision, String name) {
    UpdateQuery update = update();
    for (Field<E, ?> field : model().fields()) {
      if (!field.key() && !Set.of("revision", "create_time", "update_time").contains(field.name())) {
        update = update.set(field.name(), set.containsKey(field.name()) ? set.get(field.name())
            : field.get(changed));
      }
    }
    return returning(update.where(and(eq("uid", uid), eq("revision", revision)))).map(rows -> rows.stream()
        .findFirst().orElseThrow(() -> new PyriteException(GrpcStatus.ABORTED, "The resource " + name
            + " changed since revision " + revision + ".")));
  }
}
