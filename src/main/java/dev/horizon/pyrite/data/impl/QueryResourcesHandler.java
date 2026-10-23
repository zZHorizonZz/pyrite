package dev.horizon.pyrite.data.impl;

import dev.horizon.aquery.aip158.PageRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesRequest;
import dev.horizon.pyrite.data.v1.QueryResourcesResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.ResourceType;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class QueryResourcesHandler extends UnaryHandler<QueryResourcesRequest, QueryResourcesResponse> {

  public static final ServiceMethod<QueryResourcesRequest, QueryResourcesResponse> SERVICE_METHOD = method(DATA,
      "QueryResources", QueryResourcesRequest.getDefaultInstance());

  private final Deployment deployment;

  public QueryResourcesHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<QueryResourcesResponse> execute(QueryResourcesRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<QueryResourcesResponse> execute(QueryResourcesRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    ResourceType<?> type = schema.type(request.getType());
    String permission = request.getPermission().isEmpty() ? type.permissions().getRead() : request.getPermission();
    return type.query(subject, permission, new PageRequest(request.getFilter(), request.getOrderBy(),
        schema.options().pageSize(request.getPageSize()), request.getPageToken()), request.getShowDeleted())
        .map(page -> QueryResourcesResponse.newBuilder()
            .addAllResources(page.items().stream().map(Resource::toMessage).toList())
            .setNextPageToken(page.nextPageToken()).build());
  }
}
