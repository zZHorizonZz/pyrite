package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.ContinueFlowRequest;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ContinueFlowHandler extends UnaryHandler<ContinueFlowRequest, Flow> {

  public static final ServiceMethod<ContinueFlowRequest, Flow> SERVICE_METHOD = method(AUTHENTICATION,
      "ContinueFlow", ContinueFlowRequest.getDefaultInstance());

  private final Deployment deployment;

  public ContinueFlowHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Flow> execute(ContinueFlowRequest request) {
    return deployment.schema().compose(schema -> schema.flows().get(request.getFlow()))
        .compose(flow -> flow.answer(request));
  }
}
