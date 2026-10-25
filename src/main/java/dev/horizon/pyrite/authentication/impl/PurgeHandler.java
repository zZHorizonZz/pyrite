package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.PurgeRequest;
import dev.horizon.pyrite.authentication.v1.PurgeResponse;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class PurgeHandler extends UnaryHandler<PurgeRequest, PurgeResponse> {

  public static final ServiceMethod<PurgeRequest, PurgeResponse> SERVICE_METHOD = method(AUTHENTICATION, "Purge",
      PurgeRequest.getDefaultInstance());

  private final Deployment deployment;

  public PurgeHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<PurgeResponse> execute(PurgeRequest request) {
    return deployment.schema().compose(schema -> schema.accounts().purge())
        .map(purged -> PurgeResponse.newBuilder().setPurged(purged).build());
  }
}
