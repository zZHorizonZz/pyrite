package dev.horizon.pyrite.access.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.v1.ListSubjectsRequest;
import dev.horizon.pyrite.access.v1.ListSubjectsResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import dev.horizon.spectrum.openfga.v1.UserTypeFilter;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

public final class ListSubjectsHandler extends UnaryHandler<ListSubjectsRequest, ListSubjectsResponse> {

  public static final ServiceMethod<ListSubjectsRequest, ListSubjectsResponse> SERVICE_METHOD = method(ACCESS,
      "ListSubjects", ListSubjectsRequest.getDefaultInstance());

  private final Deployment deployment;

  public ListSubjectsHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<ListSubjectsResponse> execute(ListSubjectsRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<ListSubjectsResponse> execute(ListSubjectsRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    if (request.getSubjectType().isEmpty()) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no subject type.");
    }
    String[] parts = request.getSubjectType().split("#", 2);
    UserTypeFilter filter = parts.length == 2
        ? UserTypeFilter.newBuilder().setType(parts[0]).setRelation(parts[1]).build()
        : UserTypeFilter.newBuilder().setType(parts[0]).build();
    return schema.require(request.getObject())
        .compose(found -> {
          found.type().relation(request.getPermission());
          return found.require(subject, found.type().permissions().getGrant(), request.getContext());
        })
        .compose(found -> found.subjects(request.getPermission(), filter, request.getContext()))
        .map(subjects -> ListSubjectsResponse.newBuilder().addAllSubjects(subjects).build());
  }
}
