package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.SignRequest;
import dev.horizon.pyrite.crypto.v1.SignResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

public final class SignHandler extends UnaryHandler<SignRequest, SignResponse> {

  public static final ServiceMethod<SignRequest, SignResponse> SERVICE_METHOD = method(CRYPTOGRAPHY, "Sign",
      SignRequest.getDefaultInstance());

  private final Deployment deployment;

  public SignHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<SignResponse> execute(SignRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    if (request.getPayloadCase() == SignRequest.PayloadCase.PAYLOAD_NOT_SET) {
      throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names neither data nor claims to sign.");
    }
    return deployment.schema().compose(schema -> schema.type(TypeSystem.KEY, KeyType.class).signing(request.getKey()))
        .compose(key -> key.require(subject, "signer", request.getContext()).map(key))
        .compose(key -> request.hasClaims() ? key.jwt(request.getType(), request.getClaims())
            : key.sign(request.getData().toByteArray()));
  }
}
