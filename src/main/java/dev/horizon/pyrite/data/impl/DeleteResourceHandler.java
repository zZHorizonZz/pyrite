package dev.horizon.pyrite.data.impl;

import dev.horizon.pyrite.data.v1.DeleteResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class DeleteResourceHandler extends UnaryHandler<DeleteResourceRequest, Resource> {

  public static final ServiceMethod<DeleteResourceRequest, Resource> SERVICE_METHOD = method(DATA, "DeleteResource",
      DeleteResourceRequest.getDefaultInstance());

  private final Deployment deployment;

  public DeleteResourceHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Resource> execute(DeleteResourceRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Resource> execute(DeleteResourceRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    return schema.require(request.getName())
        .compose(found -> found.require(subject, found.type().permissions().getDelete()))
        .compose(found -> found.delete(request.getRevision()))
        .map(dev.horizon.pyrite.impl.resource.Resource::toMessage);
  }
}
