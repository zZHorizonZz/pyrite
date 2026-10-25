package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.authentication.v1.AddIdentifierRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class AddIdentifierHandler
    extends UnaryHandler<AddIdentifierRequest, dev.horizon.pyrite.authentication.v1.Identifier> {

  public static final ServiceMethod<AddIdentifierRequest, dev.horizon.pyrite.authentication.v1.Identifier> SERVICE_METHOD = method(
      AUTHENTICATION, "AddIdentifier",
      AddIdentifierRequest.getDefaultInstance());

  private final Deployment deployment;

  public AddIdentifierHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<dev.horizon.pyrite.authentication.v1.Identifier> execute(AddIdentifierRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount())
        .compose(account -> account.resource().require(subject, "admin", Struct.getDefaultInstance()).map(account))
        .compose(account -> schema.accounts().addIdentifier(account, request.getKind(), request.getValue(),
            request.getPrimary())))
        .map(Identifier::message);
  }
}
