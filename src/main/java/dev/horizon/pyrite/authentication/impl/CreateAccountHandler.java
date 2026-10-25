package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.CreateAccountRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class CreateAccountHandler
    extends UnaryHandler<CreateAccountRequest, dev.horizon.pyrite.authentication.v1.Account> {

  public static final ServiceMethod<CreateAccountRequest, dev.horizon.pyrite.authentication.v1.Account> SERVICE_METHOD = method(
      AUTHENTICATION, "CreateAccount",
      CreateAccountRequest.getDefaultInstance());

  private final Deployment deployment;

  public CreateAccountHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.authentication.v1.Account> execute(CreateAccountRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema()
        .compose(schema -> schema.accounts().create(subject, request.getAccountId(), request.getKind().name()))
        .map(Account::message);
  }
}
