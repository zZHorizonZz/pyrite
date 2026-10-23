package dev.horizon.pyrite.data.impl;

import dev.horizon.pyrite.data.v1.PurgeResourcesRequest;
import dev.horizon.pyrite.data.v1.PurgeResourcesResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class PurgeResourcesHandler extends UnaryHandler<PurgeResourcesRequest, PurgeResourcesResponse> {

  public static final ServiceMethod<PurgeResourcesRequest, PurgeResourcesResponse> SERVICE_METHOD = method(DATA,
      "PurgeResources", PurgeResourcesRequest.getDefaultInstance());

  private final Deployment deployment;

  public PurgeResourcesHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<PurgeResourcesResponse> execute(PurgeResourcesRequest request) {
    return deployment.schema().compose(Schema::purge)
        .map(purged -> PurgeResourcesResponse.newBuilder().setPurged(purged).build());
  }
}
