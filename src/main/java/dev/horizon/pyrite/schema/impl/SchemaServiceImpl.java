package dev.horizon.pyrite.schema.impl;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.impl.HandlerService;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.DeploymentImpl;
import dev.horizon.pyrite.schema.SchemaService;
import dev.horizon.pyrite.schema.v1.SchemaProto;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

public final class SchemaServiceImpl extends HandlerService implements SchemaService {

  public SchemaServiceImpl(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    super(UnaryHandler.SCHEMA, SchemaProto.getDescriptor().findServiceByName(UnaryHandler.SCHEMA.name()));
    Deployment deployment = new DeploymentImpl(vertx, pool, driver, openFga, options);
    bind(WriteDeclarationHandler.SERVICE_METHOD, new WriteDeclarationHandler(deployment));
    bind(ReadDeclarationHandler.SERVICE_METHOD, new ReadDeclarationHandler(deployment));
  }
}
