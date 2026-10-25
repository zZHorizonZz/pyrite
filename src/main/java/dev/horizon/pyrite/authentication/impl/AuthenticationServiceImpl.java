package dev.horizon.pyrite.authentication.impl;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.authentication.AuthenticationService;
import dev.horizon.pyrite.authentication.v1.AuthenticationProto;
import dev.horizon.pyrite.impl.HandlerService;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.DeploymentImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

public final class AuthenticationServiceImpl extends HandlerService implements AuthenticationService {

  public AuthenticationServiceImpl(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    super(UnaryHandler.AUTHENTICATION,
        AuthenticationProto.getDescriptor().findServiceByName(UnaryHandler.AUTHENTICATION.name()));
    Deployment deployment = new DeploymentImpl(vertx, pool, driver, openFga, options);
    bind(StartFlowHandler.SERVICE_METHOD, new StartFlowHandler(deployment));
    bind(ContinueFlowHandler.SERVICE_METHOD, new ContinueFlowHandler(deployment));
    bind(AuthenticateHandler.SERVICE_METHOD, new AuthenticateHandler(deployment));
    bind(CreateAccountHandler.SERVICE_METHOD, new CreateAccountHandler(deployment));
    bind(GetAccountHandler.SERVICE_METHOD, new GetAccountHandler(deployment));
    bind(DeleteAccountHandler.SERVICE_METHOD, new DeleteAccountHandler(deployment));
    bind(AddIdentifierHandler.SERVICE_METHOD, new AddIdentifierHandler(deployment));
    bind(ListIdentifiersHandler.SERVICE_METHOD, new ListIdentifiersHandler(deployment));
    bind(GetSessionHandler.SERVICE_METHOD, new GetSessionHandler(deployment));
    bind(EndSessionHandler.SERVICE_METHOD, new EndSessionHandler(deployment));
    bind(PurgeHandler.SERVICE_METHOD, new PurgeHandler(deployment));
  }
}
