package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.crypto.v1.DecryptRequest;
import dev.horizon.pyrite.crypto.v1.DecryptResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class DecryptHandler extends UnaryHandler<DecryptRequest, DecryptResponse> {

  public static final ServiceMethod<DecryptRequest, DecryptResponse> SERVICE_METHOD = method(CRYPTOGRAPHY, "Decrypt",
      DecryptRequest.getDefaultInstance());

  private final Deployment deployment;

  public DecryptHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<DecryptResponse> execute(DecryptRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.keys().encryption(request.getKey()))
        .compose(key -> key.resource().require(subject, "decrypter", request.getContext()).map(key))
        .compose(key -> key.decrypt(request.getCiphertext().toByteArray(),
            request.getAssociatedData().toByteArray()));
  }
}
