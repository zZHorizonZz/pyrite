package dev.horizon.pyrite.access.impl;

import dev.horizon.pyrite.access.v1.CheckRequest;
import dev.horizon.pyrite.access.v1.CheckResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Resource;
import dev.horizon.pyrite.impl.resource.Schema;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class CheckHandler extends UnaryHandler<CheckRequest, CheckResponse> {

  public static final ServiceMethod<CheckRequest, CheckResponse> SERVICE_METHOD = method(ACCESS, "Check",
      CheckRequest.getDefaultInstance());

  private final Deployment deployment;

  public CheckHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<CheckResponse> execute(CheckRequest request) {
    return deployment.schema().compose(schema -> execute(request, schema));
  }

  private Future<CheckResponse> execute(CheckRequest request, Schema schema) {
    Subject subject = Subject.parse(request.getSubject());
    Subject principal = request.getPrincipal().isEmpty() ? subject : Subject.parse(request.getPrincipal());
    return schema.require(request.getObject()).compose(found -> {
      found.relation(request.getPermission());
      Future<Resource> asked = principal.equals(subject) ? Future.succeededFuture(found)
          : found.require(subject, found.type().permissions().getGrant(), request.getContext());
      return asked.compose(ignored -> found.check(principal.toString(), request.getPermission(),
          request.getContext()));
    }).map(allowed -> CheckResponse.newBuilder().setAllowed(allowed).build());
  }
}
