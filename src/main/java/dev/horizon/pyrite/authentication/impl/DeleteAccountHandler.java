package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Empty;
import com.google.protobuf.Struct;
import dev.horizon.pyrite.authentication.v1.DeleteAccountRequest;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class DeleteAccountHandler extends UnaryHandler<DeleteAccountRequest, Empty> {

  public static final ServiceMethod<DeleteAccountRequest, Empty> SERVICE_METHOD = method(AUTHENTICATION, "DeleteAccount",
      DeleteAccountRequest.getDefaultInstance());

  private final Deployment deployment;

  public DeleteAccountHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<Empty> execute(DeleteAccountRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.accounts().account(request.getName())
        .compose(account -> account.resource().require(subject, "admin", Struct.getDefaultInstance()).map(account))
        .compose(account -> schema.accounts().delete(account)))
        .map(Empty.getDefaultInstance());
  }
}
