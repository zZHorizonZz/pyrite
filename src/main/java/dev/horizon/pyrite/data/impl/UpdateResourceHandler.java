package dev.horizon.pyrite.data.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.UpdateResourceRequest;
import dev.horizon.pyrite.data.v1.Value;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import java.util.HashMap;
import java.util.Map;

public final class UpdateResourceHandler extends UnaryHandler<UpdateResourceRequest, Resource> {

  public static final ServiceMethod<UpdateResourceRequest, Resource> SERVICE_METHOD = method(DATA, "UpdateResource",
      UpdateResourceRequest.getDefaultInstance());

  private final Deployment deployment;

  public UpdateResourceHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Resource> execute(UpdateResourceRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Resource> execute(UpdateResourceRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    if (request.getUpdateMask().getPathsCount() == 0) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The update mask names no fields.");
    }
    return schema.require(request.getResource().getName())
        .compose(found -> found.require(subject, found.type().permissions().getUpdate(), request.getContext()))
        .compose(found -> {
          Map<String, Object> changes = new HashMap<>();
          for (String path : request.getUpdateMask().getPathsList()) {
            Value value = request.getResource().getFieldsMap().get(path);
            changes.put(path, value == null ? null : found.type().parse(path, value));
          }
          return found.update(changes, request.getRevision());
        })
        .map(dev.horizon.pyrite.impl.resource.Resource::toMessage);
  }
}
