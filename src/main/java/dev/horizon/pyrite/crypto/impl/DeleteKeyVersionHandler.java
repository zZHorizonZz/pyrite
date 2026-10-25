package dev.horizon.pyrite.crypto.impl;

import com.google.protobuf.Empty;
import dev.horizon.pyrite.crypto.v1.DeleteKeyVersionRequest;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class DeleteKeyVersionHandler extends UnaryHandler<DeleteKeyVersionRequest, Empty> {

  public static final ServiceMethod<DeleteKeyVersionRequest, Empty> SERVICE_METHOD = method(CRYPTOGRAPHY, "DeleteKeyVersion",
      DeleteKeyVersionRequest.getDefaultInstance());

  private final Deployment deployment;

  public DeleteKeyVersionHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Empty> execute(DeleteKeyVersionRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.keys().version(request.getName()))
        .compose(version -> version.key()
            .compose(key -> key.resource().require(subject, "admin", request.getContext()))
            .compose(allowed -> version.delete()))
        .map(Empty.getDefaultInstance());
  }
}
