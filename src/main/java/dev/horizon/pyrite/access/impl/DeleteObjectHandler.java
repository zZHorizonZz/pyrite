package dev.horizon.pyrite.access.impl;

import com.google.protobuf.Empty;
import dev.horizon.pyrite.access.v1.DeleteObjectRequest;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class DeleteObjectHandler extends UnaryHandler<DeleteObjectRequest, Empty> {

  public static final ServiceMethod<DeleteObjectRequest, Empty> SERVICE_METHOD = method(ACCESS, "DeleteObject",
      DeleteObjectRequest.getDefaultInstance());

  private final Deployment deployment;

  public DeleteObjectHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Empty> execute(DeleteObjectRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.require(request.getName())
        .compose(found -> found.require(subject, found.type().permissions().getGrant(), request.getContext()))
        .compose(found -> schema.forget(request.getName())))
        .map(Empty.getDefaultInstance());
  }
}
