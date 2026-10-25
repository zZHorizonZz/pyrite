package dev.horizon.pyrite.access.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.v1.ListObjectsRequest;
import dev.horizon.pyrite.access.v1.ListObjectsResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.model.DeclaredType;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

public final class ListObjectsHandler extends UnaryHandler<ListObjectsRequest, ListObjectsResponse> {

  public static final ServiceMethod<ListObjectsRequest, ListObjectsResponse> SERVICE_METHOD = method(ACCESS, "ListObjects",
      ListObjectsRequest.getDefaultInstance());

  private final Deployment deployment;

  public ListObjectsHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<ListObjectsResponse> execute(ListObjectsRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> {
      DeclaredType type = schema.type(request.getType());
      if (!type.relations().containsKey(request.getPermission())) {
        throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + type.name() + " has no relation "
            + request.getPermission() + ".");
      }
      return schema.adapter().objects(type.singular(), request.getPermission(), subject.toString(),
          request.getContext()).map(
              objects -> ListObjectsResponse.newBuilder().addAllNames(objects.stream()
                  .map(object -> type.resource(object.substring(object.indexOf(':') + 1))).sorted().toList()).build());
    });
  }
}
