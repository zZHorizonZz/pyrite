package dev.horizon.pyrite.impl;

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

public record GrpcAdapter(OpenFGAServiceGrpcClient client, String id, String model, int tuplesPerRead) implements Adapter {

  @Override
  public Future<Boolean> check(String user, String relation, String object, Struct context) {
    return client.check(CheckRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setTupleKey(CheckRequestTupleKey.newBuilder().setUser(user).setRelation(relation).setObject(object))
        .setContext(context).build()).map(CheckResponse::getAllowed);
  }

  @Override
  public Future<Void> write(TupleKey tuple) {
    return client.write(WriteRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setWrites(WriteRequestWrites.newBuilder().setOnDuplicate("ignore").addTupleKeys(tuple)).build()).mapEmpty();
  }

  @Override
  public Future<Void> delete(List<TupleKeyWithoutCondition> tuples) {
    return client.write(WriteRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setDeletes(WriteRequestDeletes.newBuilder().setOnMissing("ignore").addAllTupleKeys(tuples)).build())
        .mapEmpty();
  }

  @Override
  public Future<ReadResponse> read(ReadRequestTupleKey key, int pageSize, String token) {
    return client.read(ReadRequest.newBuilder().setStoreId(id).setTupleKey(key)
        .setPageSize(Int32Value.of(Math.min(pageSize, tuplesPerRead))).setContinuationToken(token).build());
  }

  @Override
  public Future<Void> forget(ReadRequestTupleKey key) {
    return read(key, tuplesPerRead, "").compose(read -> read.getTuplesCount() == 0 ? Future.succeededFuture()
        : delete(read.getTuplesList().stream().map(tuple -> TupleKeyWithoutCondition.newBuilder()
            .setUser(tuple.getKey().getUser()).setRelation(tuple.getKey().getRelation())
            .setObject(tuple.getKey().getObject()).build()).toList()).compose(ignored -> forget(key)));
  }

  @Override
  public Future<List<String>> objects(String type, String relation, String user, Struct context) {
    return client.listObjects(ListObjectsRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setType(type).setRelation(relation).setUser(user).setContext(context).build())
        .map(ListObjectsResponse::getObjectsList);
  }

  @Override
  public Future<List<User>> users(String type, String object, String relation, UserTypeFilter filter,
      Struct context) {
    return client.listUsers(ListUsersRequest.newBuilder().setStoreId(id).setAuthorizationModelId(model)
        .setObject(dev.horizon.spectrum.openfga.v1.Object.newBuilder().setType(type).setId(object))
        .setRelation(relation).addUserFilters(filter).setContext(context).build())
        .map(ListUsersResponse::getUsersList);
  }

  @Override
  public Future<ReadChangesResponse> changes(String type, int pageSize, String token, Optional<Timestamp> start) {
    ReadChangesRequest.Builder request = ReadChangesRequest.newBuilder().setStoreId(id).setType(type)
        .setPageSize(Int32Value.of(Math.min(pageSize, tuplesPerRead))).setContinuationToken(token);
    start.ifPresent(request::setStartTime);
    return client.readChanges(request.build());
  }
}
