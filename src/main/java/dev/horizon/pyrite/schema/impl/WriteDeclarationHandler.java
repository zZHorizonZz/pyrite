package dev.horizon.pyrite.schema.impl;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.schema.v1.WriteDeclarationRequest;
import dev.horizon.pyrite.schema.v1.WriteDeclarationResponse;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class WriteDeclarationHandler extends UnaryHandler<WriteDeclarationRequest, WriteDeclarationResponse> {

  public static final ServiceMethod<WriteDeclarationRequest, WriteDeclarationResponse> SERVICE_METHOD = method(
      SCHEMA, "WriteDeclaration", WriteDeclarationRequest.getDefaultInstance());

  private final Deployment deployment;

  public WriteDeclarationHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<WriteDeclarationResponse> execute(WriteDeclarationRequest request) {
    return deployment.write(request.getDeclaration())
        .map(id -> WriteDeclarationResponse.newBuilder().setId(id).build());
  }
}
