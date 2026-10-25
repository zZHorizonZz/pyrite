package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.crypto.v1.RotateKeyRequest;

import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class RotateKeyHandler extends UnaryHandler<RotateKeyRequest, dev.horizon.pyrite.crypto.v1.Key> {

  public static final ServiceMethod<RotateKeyRequest, dev.horizon.pyrite.crypto.v1.Key> SERVICE_METHOD = method(CRYPTOGRAPHY,
      "RotateKey",
      RotateKeyRequest.getDefaultInstance());

  private final Deployment deployment;

  public RotateKeyHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.crypto.v1.Key> execute(RotateKeyRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.keys().key(request.getName()))
        .compose(key -> key.resource().require(subject, "admin", request.getContext()).map(key))
        .compose(Key::rotate)
        .map(Key::message);
  }
}
