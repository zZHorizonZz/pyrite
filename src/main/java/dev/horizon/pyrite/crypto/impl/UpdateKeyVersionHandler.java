package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.UpdateKeyVersionRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;

public final class UpdateKeyVersionHandler
    extends UnaryHandler<UpdateKeyVersionRequest, dev.horizon.pyrite.crypto.v1.KeyVersion> {

  public static final ServiceMethod<UpdateKeyVersionRequest, dev.horizon.pyrite.crypto.v1.KeyVersion> SERVICE_METHOD = method(
      CRYPTOGRAPHY, "UpdateKeyVersion",
      UpdateKeyVersionRequest.getDefaultInstance());

  private final Deployment deployment;

  public UpdateKeyVersionHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.crypto.v1.KeyVersion> execute(UpdateKeyVersionRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    boolean enabled = switch (request.getState()) {
      case ENABLED -> true;
      case DISABLED -> false;
      case STATE_UNSPECIFIED, UNRECOGNIZED -> throw new PyriteException(GrpcStatus.INVALID_ARGUMENT, "A version is"
          + " ENABLED or DISABLED.");
    };
    return deployment.schema().compose(schema -> schema.keys().version(request.getName()))
        .compose(version -> version.key()
            .compose(key -> key.resource().require(subject, "admin", request.getContext()))
            .compose(allowed -> version.update(enabled, request.getRevision())))
        .map(KeyVersion::message);
  }
}
