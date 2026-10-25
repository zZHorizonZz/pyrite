package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.crypto.v1.ListKeyVersionsRequest;
import dev.horizon.pyrite.crypto.v1.ListKeyVersionsResponse;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ListKeyVersionsHandler extends UnaryHandler<ListKeyVersionsRequest, ListKeyVersionsResponse> {

  public static final ServiceMethod<ListKeyVersionsRequest, ListKeyVersionsResponse> SERVICE_METHOD = method(CRYPTOGRAPHY,
      "ListKeyVersions",
      ListKeyVersionsRequest.getDefaultInstance());

  private final Deployment deployment;

  public ListKeyVersionsHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<ListKeyVersionsResponse> execute(ListKeyVersionsRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.keys().key(request.getKey()))
        .compose(key -> key.resource().require(subject, "viewer", request.getContext()).map(key))
        .compose(Key::versions)
        .map(versions -> ListKeyVersionsResponse.newBuilder()
            .addAllVersions(versions.stream().map(KeyVersion::message).toList()).build());
  }
}
