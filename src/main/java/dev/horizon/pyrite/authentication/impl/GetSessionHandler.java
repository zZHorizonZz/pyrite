package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.authentication.v1.GetSessionRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class GetSessionHandler extends UnaryHandler<GetSessionRequest, dev.horizon.pyrite.authentication.v1.Session> {

  public static final ServiceMethod<GetSessionRequest, dev.horizon.pyrite.authentication.v1.Session> SERVICE_METHOD = method(
      AUTHENTICATION, "GetSession",
      GetSessionRequest.getDefaultInstance());

  private final Deployment deployment;

  public GetSessionHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.authentication.v1.Session> execute(GetSessionRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.accounts().session(request.getName())
        .compose(session -> schema.accounts().account(session.message().getAccount())
            .compose(account -> account.resource().require(subject, "viewer", Struct.getDefaultInstance()))
            .map(allowed -> session.message())));
  }
}
