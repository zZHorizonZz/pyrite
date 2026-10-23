package dev.horizon.pyrite.crypto;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.crypto.impl.CryptographyServiceImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.Service;
import io.vertx.sqlclient.Pool;

/**
 * The cryptography service, {@code pyrite.crypto.v1.Cryptography}, which encrypts, decrypts, signs and verifies with
 * the keys of the type {@code pyrite.dev/Key}.
 *
 * <p>
 * Add the service to a Vert.x gRPC server: an {@code EventBusGrpcServer} serves it to clients in the same Vert.x
 * instance or cluster, and a {@code GrpcServer} to gRPC clients over the network. Call it with a
 * {@code CryptographyGrpcClient} on the matching client.
 *
 * <p>
 * Every call runs as the subject that its request names, which needs the key's relation of the operation:
 * {@code encrypter}, {@code decrypter}, {@code signer} or {@code verifier}. The material of each key version is
 * encrypted by the root key of the options, {@link PyriteServiceOptions#withRoot}.
 */
public interface CryptographyService extends Service {

  /**
   * Creates the service with the default options of {@link PyriteServiceOptions}. The default options have no root key,
   * so a declaration with {@code pyrite.dev/Key} fails with them. Give options with a root key to
   * {@link #create(Vertx, Pool, Driver, OpenFGAServiceGrpcClient, PyriteServiceOptions)}.
   *
   * @param vertx the Vert.x instance, which runs key generation and password hashing on worker threads
   * @param pool the pool of the database that holds the application's tables and Pyrite's
   * @param driver the Cinder driver of the database, such as {@code new PostgresDriver()}
   * @param openFga a client of OpenFGA's API, such as one of Spectrum's {@code OpenFgaService}
   * @return the service, to add to a gRPC server
   */
  static CryptographyService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga) {
    return create(vertx, pool, driver, openFga, new PyriteServiceOptions());
  }

  /**
   * Creates the service.
   *
   * @param vertx the Vert.x instance, which runs key generation and password hashing on worker threads
   * @param pool the pool of the database that holds the application's tables and Pyrite's
   * @param driver the Cinder driver of the database, such as {@code new PostgresDriver()}
   * @param openFga a client of OpenFGA's API, such as one of Spectrum's {@code OpenFgaService}
   * @param options the limits, the keys and the clock; give every service of Pyrite the same
   * @return the service, to add to a gRPC server
   */
  static CryptographyService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    return new CryptographyServiceImpl(vertx, pool, driver, openFga, options);
  }
}
