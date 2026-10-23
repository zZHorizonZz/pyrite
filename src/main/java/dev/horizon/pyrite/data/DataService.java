package dev.horizon.pyrite.data;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.data.impl.DataServiceImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.Service;
import io.vertx.sqlclient.Pool;

/**
 * The data service, {@code pyrite.data.v1.Data}, which creates, reads, changes and purges the resources of the declared
 * types, in the tables that the declaration names.
 *
 * <p>
 * Add the service to a Vert.x gRPC server: an {@code EventBusGrpcServer} serves it to clients in the same Vert.x
 * instance or cluster, and a {@code GrpcServer} to gRPC clients over the network. Call it with a {@code DataGrpcClient}
 * on the matching client.
 *
 * <p>
 * Every call except {@code PurgeResources} runs as the subject that its request names, and checks the permission that
 * the type declares for the method. Pyrite trusts the subject, so serve the service only to callers that set it from a
 * credential that they verified.
 */
public interface DataService extends Service {

  /**
   * Creates the service with the default options of {@link PyriteServiceOptions}.
   *
   * @param vertx the Vert.x instance, which runs key generation and password hashing on worker threads
   * @param pool the pool of the database that holds the application's tables and Pyrite's
   * @param driver the Cinder driver of the database, such as {@code new PostgresDriver()}
   * @param openFga a client of OpenFGA's API, such as one of Spectrum's {@code OpenFgaService}
   * @return the service, to add to a gRPC server
   */
  static DataService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga) {
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
  static DataService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    return new DataServiceImpl(vertx, pool, driver, openFga, options);
  }
}
