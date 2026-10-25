package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Struct;
import dev.horizon.pyrite.authentication.v1.ListIdentifiersRequest;
import dev.horizon.pyrite.authentication.v1.ListIdentifiersResponse;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.Subject;
import io.vertx.core.Future;
import io.vertx.grpc.common.ServiceMethod;

public final class ListIdentifiersHandler extends UnaryHandler<ListIdentifiersRequest, ListIdentifiersResponse> {

  public static final ServiceMethod<ListIdentifiersRequest, ListIdentifiersResponse> SERVICE_METHOD = method(AUTHENTICATION,
      "ListIdentifiers",
      ListIdentifiersRequest.getDefaultInstance());

  private final Deployment deployment;

  public ListIdentifiersHandler(Deployment deployment) {
    this.deployment = deployment;
  }

  @Override
  protected Future<ListIdentifiersResponse> execute(ListIdentifiersRequest request) {
    Subject subject = Subject.parse(request.getSubject());
    return deployment.schema().compose(schema -> schema.accounts().account(request.getAccount()))
        .compose(account -> account.resource().require(subject, "viewer", Struct.getDefaultInstance()).map(account))
        .compose(Account::identifiers)
        .map(identifiers -> ListIdentifiersResponse.newBuilder()
            .addAllIdentifiers(identifiers.stream().map(Identifier::message).toList()).build());
  }
}
