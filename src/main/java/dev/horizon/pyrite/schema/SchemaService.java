package dev.horizon.pyrite.schema;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.schema.impl.SchemaServiceImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.Service;
import io.vertx.sqlclient.Pool;

/**
 * The schema service, {@code pyrite.schema.v1.Schema}, which writes and reads the declaration of the application's
 * types, conditions and flows.
 *
 * <p>
 * Add the service to a Vert.x gRPC server: an {@code EventBusGrpcServer} serves it to clients in the same Vert.x
 * instance or cluster, and a {@code GrpcServer} to gRPC clients over the network. Call it with a
 * {@code SchemaGrpcClient} on the matching client.
 *
 * <p>
 * Write a declaration before any call of the other services. {@code WriteDeclaration} checks it, writes its
 * authorization model to the OpenFGA store {@code pyrite}, which the first write creates, and stores it in the table
 * {@code pyrite_declaration}. Every service reads the latest declaration at each call.
 */
public interface SchemaService extends Service {

  /**
   * Creates the service with the default options of {@link PyriteServiceOptions}.
   *
   * @param vertx the Vert.x instance, which runs key generation and password hashing on worker threads
   * @param pool the pool of the database that holds the application's tables and Pyrite's
   * @param driver the Cinder driver of the database, such as {@code new PostgresDriver()}
   * @param openFga a client of OpenFGA's API, such as one of Spectrum's {@code OpenFgaService}
   * @return the service, to add to a gRPC server
   */
  static SchemaService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga) {
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
  static SchemaService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    return new SchemaServiceImpl(vertx, pool, driver, openFga, options);
  }
}
