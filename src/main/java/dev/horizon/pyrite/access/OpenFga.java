package dev.horizon.pyrite.access;

import com.google.protobuf.Int32Value;
import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import dev.horizon.spectrum.openfga.v1.CheckRequest;
import dev.horizon.spectrum.openfga.v1.CheckRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.CheckResponse;
import dev.horizon.spectrum.openfga.v1.ListObjectsRequest;
import dev.horizon.spectrum.openfga.v1.ListObjectsResponse;
import dev.horizon.spectrum.openfga.v1.ListUsersRequest;
import dev.horizon.spectrum.openfga.v1.ListUsersResponse;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import dev.horizon.spectrum.openfga.v1.ReadChangesRequest;
import dev.horizon.spectrum.openfga.v1.ReadChangesResponse;
import dev.horizon.spectrum.openfga.v1.ReadRequest;
import dev.horizon.spectrum.openfga.v1.ReadRequestTupleKey;
import dev.horizon.spectrum.openfga.v1.ReadResponse;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import dev.horizon.spectrum.openfga.v1.TupleKeyWithoutCondition;
import dev.horizon.spectrum.openfga.v1.User;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import dev.horizon.spectrum.openfga.v1.WriteRequest;
import dev.horizon.spectrum.openfga.v1.WriteRequestDeletes;
import dev.horizon.spectrum.openfga.v1.WriteRequestWrites;
import io.vertx.core.Future;
import java.util.List;
import java.util.Optional;

/**
 * Pyrite's calls to OpenFGA's API, on the store and the authorization model of one declaration.
 *
 * <p>
 * Objects, users and relations are OpenFGA's: an object is a type's singular name and an id, such as
 * {@code project:ledger}. The adapter knows nothing of Pyrite's hierarchy: a {@code parent} tuple is a tuple to it as
 * any other. Each call fails with the failure of OpenFGA, such as an {@code InvalidStatusException} with its status.
 *
 * @param client the client of OpenFGA's API
 * @param id the id of the store
 * @param model the id of the authorization model of the declaration
 * @param tuplesPerRead the most tuples that one read takes
 */
public record OpenFga(OpenFGAServiceGrpcClient client, String id, String model, int tuplesPerRead) {

  /**
   * Asks whether a user has a relation on an object.
   *
   * @param user the user, such as {@code user:bob}, or a set, such as {@code project:ledger#editor}
   * @param relation the relation, such as {@code can_view}
   * @param object the object, such as {@code project:ledger}
   * @param context the values that the conditions of tuples read
   * @return whether the user has the relation
   */
  public Future<Boolean> check(String user, String relation, String object, Struct context) {
    return client.check(CheckRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setTupleKey(CheckRequestTupleKey.newBuilder().setUser(user).setRelation(relation).setObject(object))
        .setContext(context).build()).map(CheckResponse::getAllowed);
  }

  /**
   * Writes a tuple. Writing one that the store holds changes nothing.
   *
   * @param tuple the tuple, with its condition, if any
   * @return a future that completes once the store holds the tuple
   */
  public Future<Void> write(TupleKey tuple) {
    return client.write(WriteRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setWrites(WriteRequestWrites.newBuilder().setOnDuplicate("ignore").addTupleKeys(tuple)).build()).mapEmpty();
  }

  /**
   * Deletes tuples. Deleting one that the store does not hold changes nothing.
   *
   * @param tuples the tuples, in one write of OpenFGA
   * @return a future that completes once the store holds none of them
   */
  public Future<Void> delete(List<TupleKeyWithoutCondition> tuples) {
    return client.write(WriteRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setDeletes(WriteRequestDeletes.newBuilder().setOnMissing("ignore").addAllTupleKeys(tuples)).build())
        .mapEmpty();
  }

  /**
   * Reads a page of the tuples that match a key.
   *
   * @param key the object, and the relation and the user to match, if any
   * @param pageSize the most tuples to read, up to {@code tuplesPerRead}
   * @param token the continuation token of the page before; empty for the first page
   * @return the tuples, with the continuation token of the next page
   */
  public Future<ReadResponse> read(ReadRequestTupleKey key, int pageSize, String token) {
    return client.read(ReadRequest.newBuilder().setStoreId(id).setTupleKey(key)
        .setPageSize(Int32Value.of(Math.min(pageSize, tuplesPerRead))).setContinuationToken(token).build());
  }

  /**
   * Deletes every tuple that matches a key, {@code tuplesPerRead} at a time, until none is left.
   *
   * @param key the object, such as {@code project:ledger} or every object of a type, {@code project:}, and the user
   * to match, if any
   * @return a future that completes once the store holds no tuple of the key
   */
  public Future<Void> forget(ReadRequestTupleKey key) {
    return read(key, tuplesPerRead, "").compose(read -> read.getTuplesCount() == 0 ? Future.succeededFuture()
        : delete(read.getTuplesList().stream().map(tuple -> TupleKeyWithoutCondition.newBuilder()
            .setUser(tuple.getKey().getUser()).setRelation(tuple.getKey().getRelation())
            .setObject(tuple.getKey().getObject()).build()).toList()).compose(ignored -> forget(key)));
  }

  /**
   * Lists the objects of a type that a user has a relation on.
   *
   * @param type the singular name of the type, such as {@code project}
   * @param relation the relation, such as {@code can_view}
   * @param user the user, such as {@code user:bob}
   * @param context the values that the conditions of tuples read
   * @return the objects, such as {@code project:ledger}, in the order of OpenFGA
   */
  public Future<List<String>> objects(String type, String relation, String user, Struct context) {
    return client.listObjects(ListObjectsRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setType(type).setRelation(relation).setUser(user).setContext(context).build())
        .map(ListObjectsResponse::getObjectsList);
  }

  /**
   * Lists the users of a type that have a relation on an object.
   *
   * @param type the singular name of the type of the object, such as {@code project}
   * @param object the id of the object, such as {@code ledger}
   * @param relation the relation, such as {@code can_view}
   * @param filter the type of the users, such as {@code user}, or of a set, such as {@code group#member}
   * @param context the values that the conditions of tuples read
   * @return the users, in the order of OpenFGA
   */
  public Future<List<User>> users(String type, String object, String relation, UserTypeFilter filter,
      Struct context) {
    return client.listUsers(ListUsersRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setObject(dev.horizon.spectrum.openfga.v1.Object.newBuilder().setType(type).setId(object))
        .setRelation(relation).addUserFilters(filter).setContext(context).build())
        .map(ListUsersResponse::getUsersList);
  }

  /**
   * Reads a page of the changes of the tuples on the objects of a type, oldest first.
   *
   * @param type the singular name of the type, such as {@code project}
   * @param pageSize the most changes to read, up to {@code tuplesPerRead}
   * @param token the continuation token of the page before; empty for the first page
   * @param start the time of the first change to read; empty for the first change of the store
   * @return the changes, with the continuation token of the next page
   */
  public Future<ReadChangesResponse> changes(String type, int pageSize, String token, Optional<Timestamp> start) {
    ReadChangesRequest.Builder request = ReadChangesRequest.newBuilder().setStoreId(id).setType(type)
        .setPageSize(Int32Value.of(Math.min(pageSize, tuplesPerRead))).setContinuationToken(token);
    start.ifPresent(request::setStartTime);
    return client.readChanges(request.build());
  }
}
