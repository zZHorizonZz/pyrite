package dev.horizon.pyrite.data.impl;

import dev.horizon.pyrite.data.v1.GetResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class GetResourceHandler extends UnaryHandler<GetResourceRequest, Resource> {

  public static final ServiceMethod<GetResourceRequest, Resource> SERVICE_METHOD = method(DATA, "GetResource",
      GetResourceRequest.getDefaultInstance());

  private final Deployment deployment;

  public GetResourceHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Resource> execute(GetResourceRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Resource> execute(GetResourceRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    return schema.require(request.getName())
        .compose(found -> found.require(subject, found.type().permissions().getRead(), request.getContext()))
        .map(dev.horizon.pyrite.impl.resource.Resource::toMessage);
  }
}
