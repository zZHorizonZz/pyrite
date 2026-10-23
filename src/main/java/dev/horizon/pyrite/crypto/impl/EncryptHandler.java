package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.crypto.v1.EncryptRequest;
import dev.horizon.pyrite.crypto.v1.EncryptResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class EncryptHandler extends UnaryHandler<EncryptRequest, EncryptResponse> {

  public static final ServiceMethod<EncryptRequest, EncryptResponse> SERVICE_METHOD = method(CRYPTOGRAPHY, "Encrypt",
      EncryptRequest.getDefaultInstance());

  private final Deployment deployment;

  public EncryptHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<EncryptResponse> execute(EncryptRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.type(TypeSystem.KEY, KeyType.class).encryption(request.getKey()))
        .compose(key -> key.require(subject, "encrypter").map(key))
        .compose(key -> key.encrypt(request.getPlaintext().toByteArray(), request.getAssociatedData().toByteArray()));
  }
}
