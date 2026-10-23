package dev.horizon.pyrite.crypto.impl;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.crypto.CryptographyService;
import dev.horizon.pyrite.crypto.v1.CryptoProto;
import dev.horizon.pyrite.impl.HandlerService;
import dev.horizon.pyrite.impl.UnaryHandler;
import dev.horizon.pyrite.impl.resource.Deployment;
import dev.horizon.pyrite.impl.resource.DeploymentImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;

public final class CryptographyServiceImpl extends HandlerService implements CryptographyService {

  public CryptographyServiceImpl(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    super(UnaryHandler.CRYPTOGRAPHY, CryptoProto.getDescriptor().findServiceByName(UnaryHandler.CRYPTOGRAPHY.name()));
    Deployment deployment = new DeploymentImpl(vertx, pool, driver, openFga, options);
    bind(EncryptHandler.SERVICE_METHOD, new EncryptHandler(deployment));
    bind(DecryptHandler.SERVICE_METHOD, new DecryptHandler(deployment));
    bind(SignHandler.SERVICE_METHOD, new SignHandler(deployment));
    bind(VerifyHandler.SERVICE_METHOD, new VerifyHandler(deployment));
  }
}
