package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.authentication.v1.GetAccountRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class GetAccountHandler extends UnaryHandler<GetAccountRequest, dev.horizon.pyrite.authentication.v1.Account> {

  public static final ServiceMethod<GetAccountRequest, dev.horizon.pyrite.authentication.v1.Account> SERVICE_METHOD = method(
      AUTHENTICATION, "GetAccount",
      GetAccountRequest.getDefaultInstance());

  private final Deployment deployment;

  public GetAccountHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.authentication.v1.Account> execute(GetAccountRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.accounts().account(request.getName()))
        .compose(account -> account.resource().require(subject, "viewer", Struct.getDefaultInstance()).map(account))
        .map(Account::message);
  }
}
