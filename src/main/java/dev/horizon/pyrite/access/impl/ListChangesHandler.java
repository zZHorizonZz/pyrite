package dev.horizon.pyrite.access.impl;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.access.v1.Change;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.access.v1.ListChangesRequest;
import dev.horizon.pyrite.access.v1.ListChangesResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.spectrum.openfga.v1.TupleChange;
import dev.horizon.spectrum.openfga.v1.TupleOperation;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.List;
import java.util.Optional;

public final class ListChangesHandler extends UnaryHandler<ListChangesRequest, ListChangesResponse> {

  public static final ServiceMethod<ListChangesRequest, ListChangesResponse> SERVICE_METHOD = method(ACCESS,
      "ListChanges", ListChangesRequest.getDefaultInstance());

  private final Deployment deployment;

  public ListChangesHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<ListChangesResponse> execute(ListChangesRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<ListChangesResponse> execute(ListChangesRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    ResourceType<?> type = schema.type(request.getType());
    return schema.adapter().changes(type.declaration().singular(),
        schema.options().pageSize(request.getPageSize()), request.getPageToken(),
        request.hasStartTime() ? Optional.of(request.getStartTime()) : Optional.empty())
        .compose(changes -> {
          List<Future<Optional<Change>>> visible = changes.getChangesList().stream()
              .map(change -> visible(schema, subject, change, request.getContext())).toList();
          return Future.all(visible).map(done -> {
            ListChangesResponse.Builder response = ListChangesResponse.newBuilder()
                .setNextPageToken(changes.getContinuationToken());
            visible.forEach(change -> change.result().ifPresent(response::addChanges));
            return response.build();
          });
        });
  }

  private static Future<Optional<Change>> visible(Schema schema, Subject subject, TupleChange change,
      Struct context) {
    Optional<Grant> grant = schema.grant(change.getTupleKey());
    if (grant.isEmpty()) {
      return Future.succeededFuture(Optional.empty());
    }
    return schema.get(grant.get().getObject())
        .compose(found -> found.isEmpty() ? Future.succeededFuture(false)
            : found.get().check(subject.toString(), found.get().type().permissions().getGrant(),
                context))
        .map(allowed -> allowed ? Optional.of(Change.newBuilder().setGrant(grant.get())
            .setOperation(change.getOperation() == TupleOperation.TUPLE_OPERATION_DELETE
                ? Change.Operation.REVOKED
                : Change.Operation.GRANTED)
            .setTime(change.getTimestamp()).build()) : Optional.empty());
  }
}
