package dev.horizon.pyrite.schema.impl;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.schema.v1.ReadDeclarationRequest;
import dev.horizon.pyrite.schema.v1.ReadDeclarationResponse;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ReadDeclarationHandler extends UnaryHandler<ReadDeclarationRequest, ReadDeclarationResponse> {

  public static final ServiceMethod<ReadDeclarationRequest, ReadDeclarationResponse> SERVICE_METHOD = method(
      SCHEMA, "ReadDeclaration", ReadDeclarationRequest.getDefaultInstance());

  private final Deployment deployment;

  public ReadDeclarationHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<ReadDeclarationResponse> execute(ReadDeclarationRequest request) {
    return deployment.read(request.getId());
  }
}
