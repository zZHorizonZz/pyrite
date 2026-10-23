package dev.horizon.pyrite.access.impl;

import dev.horizon.pyrite.access.v1.ListGrantsRequest;
import dev.horizon.pyrite.access.v1.ListGrantsResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ListGrantsHandler extends UnaryHandler<ListGrantsRequest, ListGrantsResponse> {

  public static final ServiceMethod<ListGrantsRequest, ListGrantsResponse> SERVICE_METHOD = method(ACCESS,
      "ListGrants", ListGrantsRequest.getDefaultInstance());

  private final Deployment deployment;

  public ListGrantsHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<ListGrantsResponse> execute(ListGrantsRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<ListGrantsResponse> execute(ListGrantsRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    return schema.require(request.getObject())
        .compose(found -> found.require(subject, found.type().permissions().getGrant(), request.getContext()))
        .compose(found -> found.grants(request.getRelation(), request.getGrantee(),
            schema.options().pageSize(request.getPageSize()), request.getPageToken()))
        .map(page -> ListGrantsResponse.newBuilder().addAllGrants(page.items())
            .setNextPageToken(page.nextPageToken()).build());
  }
}
