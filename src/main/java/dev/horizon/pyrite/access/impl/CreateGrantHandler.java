package dev.horizon.pyrite.access.impl;

import dev.horizon.pyrite.access.v1.CreateGrantRequest;
import dev.horizon.pyrite.access.v1.Grant;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class CreateGrantHandler extends UnaryHandler<CreateGrantRequest, Grant> {

  public static final ServiceMethod<CreateGrantRequest, Grant> SERVICE_METHOD = method(ACCESS, "CreateGrant",
      CreateGrantRequest.getDefaultInstance());

  private final Deployment deployment;

  public CreateGrantHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Grant> execute(CreateGrantRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Grant> execute(CreateGrantRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    return schema.require(request.getGrant().getObject())
        .compose(target -> target.require(subject, target.type().permissions().getGrant(), request.getContext()))
        .compose(target -> target.grant(request.getGrant(), request.getContext()))
        .map(request.getGrant());
  }
}
