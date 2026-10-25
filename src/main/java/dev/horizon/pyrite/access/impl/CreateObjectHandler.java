package dev.horizon.pyrite.access.impl;

import com.google.protobuf.Empty;
import dev.horizon.pyrite.access.v1.CreateObjectRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class CreateObjectHandler extends UnaryHandler<CreateObjectRequest, Empty> {

  public static final ServiceMethod<CreateObjectRequest, Empty> SERVICE_METHOD = method(ACCESS, "CreateObject",
      CreateObjectRequest.getDefaultInstance());

  private final Deployment deployment;

  public CreateObjectHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Empty> execute(CreateObjectRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema()
        .compose(schema -> schema.create(subject, request.getName(), request.getParent(), request.getContext()))
        .map(Empty.getDefaultInstance());
  }
}
