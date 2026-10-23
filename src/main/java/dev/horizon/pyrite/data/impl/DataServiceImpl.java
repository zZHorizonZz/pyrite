package dev.horizon.pyrite.data.impl;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.data.DataService;
import dev.horizon.pyrite.data.v1.DataProto;
import dev.horizon.pyrite.impl.HandlerService;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.DeploymentImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

public final class DataServiceImpl extends HandlerService implements DataService {

  public DataServiceImpl(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    super(UnaryHandler.DATA, DataProto.getDescriptor().findServiceByName(UnaryHandler.DATA.name()));
    Deployment deployment = new DeploymentImpl(vertx, pool, driver, openFga, options);
    bind(CreateResourceHandler.SERVICE_METHOD, new CreateResourceHandler(deployment));
    bind(GetResourceHandler.SERVICE_METHOD, new GetResourceHandler(deployment));
    bind(QueryResourcesHandler.SERVICE_METHOD, new QueryResourcesHandler(deployment));
    bind(UpdateResourceHandler.SERVICE_METHOD, new UpdateResourceHandler(deployment));
    bind(MoveResourceHandler.SERVICE_METHOD, new MoveResourceHandler(deployment));
    bind(DeleteResourceHandler.SERVICE_METHOD, new DeleteResourceHandler(deployment));
    bind(UndeleteResourceHandler.SERVICE_METHOD, new UndeleteResourceHandler(deployment));
    bind(PurgeResourcesHandler.SERVICE_METHOD, new PurgeResourcesHandler(deployment));
  }
}
