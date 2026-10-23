package dev.horizon.pyrite.data.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.data.v1.MoveResourceRequest;
import dev.horizon.pyrite.data.v1.Resource;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

public final class MoveResourceHandler extends UnaryHandler<MoveResourceRequest, Resource> {

  public static final ServiceMethod<MoveResourceRequest, Resource> SERVICE_METHOD = method(DATA, "MoveResource",
      MoveResourceRequest.getDefaultInstance());

  private final Deployment deployment;

  public MoveResourceHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Resource> execute(MoveResourceRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<Resource> execute(MoveResourceRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    return schema.require(request.getName())
        .compose(found -> found.require(subject, found.type().permissions().getUpdate()))
        .compose(found -> {
          if (found.type().declaration().parents().isEmpty()) {
            throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The type " + found.type().name() + " has no"
                + " parents; its resources do not move.");
          }
          return found.type().place(request.getParent())
              .compose(parent -> parent.orElseThrow().require(subject, found.type().permissions().getCreate()))
              .compose(parent -> found.move(parent, request.getRevision()));
        })
        .map(dev.horizon.pyrite.impl.resource.Resource::toMessage);
  }
}
