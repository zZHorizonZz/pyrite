package dev.horizon.pyrite.access.impl;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.access.AccessService;
import dev.horizon.pyrite.access.v1.AccessProto;
import dev.horizon.pyrite.impl.HandlerService;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.DeploymentImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

public final class AccessServiceImpl extends HandlerService implements AccessService {

  public AccessServiceImpl(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    super(UnaryHandler.ACCESS, AccessProto.getDescriptor().findServiceByName(UnaryHandler.ACCESS.name()));
    Deployment deployment = new DeploymentImpl(vertx, pool, driver, openFga, options);
    bind(CreateObjectHandler.SERVICE_METHOD, new CreateObjectHandler(deployment));
    bind(DeleteObjectHandler.SERVICE_METHOD, new DeleteObjectHandler(deployment));
    bind(ListObjectsHandler.SERVICE_METHOD, new ListObjectsHandler(deployment));
    bind(CreateGrantHandler.SERVICE_METHOD, new CreateGrantHandler(deployment));
    bind(DeleteGrantHandler.SERVICE_METHOD, new DeleteGrantHandler(deployment));
    bind(CheckHandler.SERVICE_METHOD, new CheckHandler(deployment));
    bind(ListGrantsHandler.SERVICE_METHOD, new ListGrantsHandler(deployment));
    bind(ListSubjectsHandler.SERVICE_METHOD, new ListSubjectsHandler(deployment));
    bind(ListChangesHandler.SERVICE_METHOD, new ListChangesHandler(deployment));
  }
}
