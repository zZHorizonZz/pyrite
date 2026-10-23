package dev.horizon.pyrite.impl;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import dev.horizon.spectrum.openfga.v1.ReadChangesResponse;
import dev.horizon.spectrum.openfga.v1.ReadRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.ReadResponse;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

/**
 * Pyrite's calls to OpenFGA's API, on the store and the authorization model of one declaration.
 *
 * <p>
 * Objects, users and relations are OpenFGA's: an object is a type's singular name and an id, such as
 * {@code project:ledger}. The adapter knows nothing of Pyrite's hierarchy; it never sees a {@code parent} tuple.
 */
public interface Adapter {

  /**
   * Checks whether a user has a relation on an object, as OpenFGA answers it.
   *
   * @param user the user, such as {@code user:anne} or {@code group:ops#member}
   * @param relation the relation
   * @param object the object, such as {@code project:ledger}
   * @param context the values of the conditions' parameters that the grants do not hold
   * @return whether the user has the relation
   */
  Future<Boolean> check(String user, String relation, String object, Struct context);

  /**
   * Writes a tuple. Writing one that exists changes nothing.
   *
   * @param tuple the tuple
   * @return when the tuple is written
   */
  Future<Void> write(TupleKey tuple);

  /**
   * Deletes tuples. Deleting one that does not exist changes nothing.
   *
   * @param tuples the tuples
   * @return when the tuples are deleted
   */
  Future<Void> delete(List<TupleKeyWithoutCondition> tuples);

  /**
   * Reads a page of the tuples that match a key.
   *
   * @param key the object, relation and user to match
   * @param pageSize the most tuples, up to {@code tuplesPerRead}
   * @param token the continuation token; empty for the first page
   * @return the page
   */
  Future<ReadResponse> read(ReadRequestTupleKey key, int pageSize, String token);

  /**
   * Deletes every tuple that matches a key, a page at a time.
   *
   * @param key the object, relation and user to match
   * @return when no tuple matches
   */
  Future<Void> forget(ReadRequestTupleKey key);

  /**
   * Lists the objects of a type on which a user has a relation, with OpenFGA's ListObjects.
   *
   * @param type the type, such as {@code project}
   * @param relation the relation
   * @param user the user
   * @param context the values of the conditions' parameters
   * @return the objects, such as {@code project:ledger}
   */
  Future<List<String>> objects(String type, String relation, String user, Struct context);

  /**
   * Lists the users that have a relation on an object, with OpenFGA's ListUsers.
   *
   * @param type the object's type
   * @param object the object's id
   * @param relation the relation
   * @param filter the type of the users, and their relation for a set
   * @param context the values of the conditions' parameters
   * @return the users
   */
  Future<List<User>> users(String type, String object, String relation, UserTypeFilter filter, Struct context);

  /**
   * Reads a page of the changes to the tuples of a type, oldest first.
   *
   * @param type the type
   * @param pageSize the most changes, up to {@code tuplesPerRead}
   * @param token the continuation token; empty for the first page
   * @param start the time of the first change, when the token is empty
   * @return the page
   */
  Future<ReadChangesResponse> changes(String type, int pageSize, String token, Optional<Timestamp> start);
}
