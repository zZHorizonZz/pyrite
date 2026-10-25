package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.crypto.v1.GetKeyRequest;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class GetKeyHandler extends UnaryHandler<GetKeyRequest, dev.horizon.pyrite.crypto.v1.Key> {

  public static final ServiceMethod<GetKeyRequest, dev.horizon.pyrite.crypto.v1.Key> SERVICE_METHOD = method(CRYPTOGRAPHY,
      "GetKey",
      GetKeyRequest.getDefaultInstance());

  private final Deployment deployment;

  public GetKeyHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.crypto.v1.Key> execute(GetKeyRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.keys().key(request.getName()))
        .compose(key -> key.resource().require(subject, "viewer", request.getContext()).map(key))
        .map(Key::message);
  }
}
