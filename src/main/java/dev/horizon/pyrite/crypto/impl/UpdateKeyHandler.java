package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.crypto.v1.UpdateKeyRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;
import java.time.Duration;

public final class UpdateKeyHandler extends UnaryHandler<UpdateKeyRequest, dev.horizon.pyrite.crypto.v1.Key> {

  public static final ServiceMethod<UpdateKeyRequest, dev.horizon.pyrite.crypto.v1.Key> SERVICE_METHOD = method(CRYPTOGRAPHY,
      "UpdateKey",
      UpdateKeyRequest.getDefaultInstance());

  private final Deployment deployment;

  public UpdateKeyHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.crypto.v1.Key> execute(UpdateKeyRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    Duration period = Duration.ofSeconds(request.getRotationPeriod().getSeconds(), request.getRotationPeriod()
        .getNanos());
    return deployment.schema().compose(schema -> schema.keys().key(request.getName()))
        .compose(key -> key.resource().require(subject, "admin", request.getContext()).map(key))
        .compose(key -> key.update(period, request.getRevision()))
        .map(Key::message);
  }
}
