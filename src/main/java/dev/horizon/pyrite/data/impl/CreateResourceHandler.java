package dev.horizon.pyrite.data.impl;

import dev.horizon.pyrite.data.v1.CreateResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.util.HashMap;
import java.util.Map;

public final class CreateResourceHandler extends UnaryHandler<CreateResourceRequest, Resource> {

  public static final ServiceMethod<CreateResourceRequest, Resource> SERVICE_METHOD = method(DATA, "CreateResource",
      CreateResourceRequest.getDefaultInstance());

  private final Deployment deployment;

  public CreateResourceHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Resource> execute(CreateResourceRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Resource> execute(CreateResourceRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    ResourceType<?> type = schema.type(request.getResource().getType());
    Map<String, Object> values = new HashMap<>();
    request.getResource().getFieldsMap().forEach((field, value) -> values.put(field, type.parse(field, value)));
    return type.create(subject, request.getParent(), request.getResourceId(), values)
        .map(dev.horizon.pyrite.impl.resource.Resource::toMessage);
  }
}
