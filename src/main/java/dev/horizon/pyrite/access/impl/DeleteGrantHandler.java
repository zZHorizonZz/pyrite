package dev.horizon.pyrite.access.impl;

import com.google.protobuf.Empty;
import dev.horizon.pyrite.access.v1.DeleteGrantRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class DeleteGrantHandler extends UnaryHandler<DeleteGrantRequest, Empty> {

  public static final ServiceMethod<DeleteGrantRequest, Empty> SERVICE_METHOD = method(ACCESS, "DeleteGrant",
      DeleteGrantRequest.getDefaultInstance());

  private final Deployment deployment;

  public DeleteGrantHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Empty> execute(DeleteGrantRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Empty> execute(DeleteGrantRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    return schema.require(request.getGrant().getObject())
        .compose(target -> target.require(subject, target.type().permissions().getGrant(), request.getContext()))
        .compose(target -> target.revoke(request.getGrant()))
        .map(Empty.getDefaultInstance());
  }
}
