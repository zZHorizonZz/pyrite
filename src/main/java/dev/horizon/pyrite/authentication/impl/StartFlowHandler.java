package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.authentication.v1.StartFlowRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class StartFlowHandler extends UnaryHandler<StartFlowRequest, Flow> {

  public static final ServiceMethod<StartFlowRequest, Flow> SERVICE_METHOD = method(AUTHENTICATION, "StartFlow",
      StartFlowRequest.getDefaultInstance());

  private final Deployment deployment;

  public StartFlowHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Flow> execute(StartFlowRequest request) {
    return deployment.schema().compose(schema -> schema.flows().start(request));
  }
}
