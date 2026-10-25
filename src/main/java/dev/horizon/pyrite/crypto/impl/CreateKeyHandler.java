package dev.horizon.pyrite.crypto.impl;

import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.crypto.v1.CreateKeyRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import io.vertx.grpc.common.ServiceMethod;
import java.time.Duration;
import java.util.Arrays;

public final class CreateKeyHandler extends UnaryHandler<CreateKeyRequest, dev.horizon.pyrite.crypto.v1.Key> {

  public static final ServiceMethod<CreateKeyRequest, dev.horizon.pyrite.crypto.v1.Key> SERVICE_METHOD = method(CRYPTOGRAPHY,
      "CreateKey",
      CreateKeyRequest.getDefaultInstance());

  private final Deployment deployment;

  public CreateKeyHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.crypto.v1.Key> execute(CreateKeyRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    Algorithm algorithm = Arrays.stream(Algorithm.values())
        .filter(known -> known.name().equals(request.getAlgorithm().name())).findFirst()
        .orElseThrow(() -> new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The request names no algorithm, one"
            + " of " + Arrays.toString(Algorithm.values()) + "."));
    Duration period = Duration.ofSeconds(request.getRotationPeriod().getSeconds(), request.getRotationPeriod()
        .getNanos());
    return deployment.schema()
        .compose(schema -> schema.keys().create(subject, request.getKeyId(), request.getParent(), algorithm, period,
            request.getContext()))
        .map(Key::message);
  }
}
