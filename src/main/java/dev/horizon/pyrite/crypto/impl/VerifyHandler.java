package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.VerifyRequest;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

public final class VerifyHandler extends UnaryHandler<VerifyRequest, VerifyResponse> {

  public static final ServiceMethod<VerifyRequest, VerifyResponse> SERVICE_METHOD = method(CRYPTOGRAPHY, "Verify",
      VerifyRequest.getDefaultInstance());

  private final Deployment deployment;

  public VerifyHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<VerifyResponse> execute(VerifyRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    if (request.getPayloadCase() == VerifyRequest.PayloadCase.PAYLOAD_NOT_SET) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names neither a signature nor a token to"
          + " verify.");
    }
    return deployment.schema().compose(schema -> schema.type(TypeSystem.KEY, KeyType.class).signing(request.getKey()))
        .compose(key -> key.require(subject, "verifier").map(key))
        .compose(key -> request.hasDetached()
            ? key.verify(request.getDetached().getData().toByteArray(),
                request.getDetached().getSignature().toByteArray(), request.getDetached().getKeyVersion())
            : key.verify(request.getToken(), request.getExpected()));
  }
}
