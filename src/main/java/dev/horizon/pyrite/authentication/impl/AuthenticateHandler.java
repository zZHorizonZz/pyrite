package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.AuthenticateRequest;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.model.TypeSystem;
import dev.horizon.pyrite.impl.resource.Deployment;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

public final class AuthenticateHandler extends UnaryHandler<AuthenticateRequest, Principal> {

  public static final ServiceMethod<AuthenticateRequest, Principal> SERVICE_METHOD = method(AUTHENTICATION,
      "Authenticate", AuthenticateRequest.getDefaultInstance());

  private final Deployment deployment;

  public AuthenticateHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Principal> execute(AuthenticateRequest request) {
    return switch (request.getCredentialCase()) {
      case SESSION_TOKEN -> deployment.schema().compose(schema -> schema.type(TypeSystem.SESSION,
          SessionType.class).authenticate(request.getSessionToken()));
      case CREDENTIAL_NOT_SET -> throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no"
          + " credential.");
    };
  }
}
