package dev.horizon.pyrite.data.impl;

import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.data.v1.UndeleteResourceRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class UndeleteResourceHandler extends UnaryHandler<UndeleteResourceRequest, Resource> {

  public static final ServiceMethod<UndeleteResourceRequest, Resource> SERVICE_METHOD = method(DATA,
      "UndeleteResource", UndeleteResourceRequest.getDefaultInstance());

  private final Deployment deployment;

  public UndeleteResourceHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Resource> execute(UndeleteResourceRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Resource> execute(UndeleteResourceRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    return schema.require(request.getName())
        .compose(found -> found.require(subject, found.type().permissions().getDelete(), request.getContext()))
        .compose(found -> found.undelete(request.getRevision()))
        .map(dev.horizon.pyrite.impl.resource.Resource::toMessage);
  }
}
