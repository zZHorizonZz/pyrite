package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import dev.horizon.pyrite.authentication.v1.EndSessionRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class EndSessionHandler extends UnaryHandler<EndSessionRequest, Empty> {

  public static final ServiceMethod<EndSessionRequest, Empty> SERVICE_METHOD = method(AUTHENTICATION, "EndSession",
      EndSessionRequest.getDefaultInstance());

  private final Deployment deployment;

  public EndSessionHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Empty> execute(EndSessionRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.accounts().session(request.getName())
        .compose(session -> schema.accounts().account(session.message().getAccount())
            .compose(account -> account.resource().require(subject, "admin", Struct.getDefaultInstance()))
            .compose(allowed -> session.end())))
        .map(Empty.getDefaultInstance());
  }
}
