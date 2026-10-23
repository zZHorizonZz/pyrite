package dev.horizon.pyrite.authentication;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.PyriteServiceOptions;
import dev.horizon.pyrite.authentication.impl.AuthenticationServiceImpl;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.Service;
import io.vertx.sqlclient.Pool;

/**
 * The authentication service, {@code pyrite.authentication.v1.Authentication}, which runs the sign-in, verification,
 * recovery and enrolment flows of the declaration, and authenticates the sessions that they issue.
 *
 * <p>
 * Add the service to a Vert.x gRPC server: an {@code EventBusGrpcServer} serves it to clients in the same Vert.x
 * instance or cluster, and a {@code GrpcServer} to gRPC clients over the network. Call it with a
 * {@code AuthenticationGrpcClient} on the matching client.
 *
 * <p>
 * The declaration has {@code pyrite.dev/Account} and {@code pyrite.dev/Key}, and the options have the options of
 * authentication, {@link PyriteServiceOptions#withAuthentication}. The two keys that those options name must exist
 * before a flow issues a session or enrols a factor.
 */
public interface AuthenticationService extends Service {

  /**
   * Creates the service with the default options of {@link PyriteServiceOptions}. The default options have no options
   * of authentication, so a declaration with {@code pyrite.dev/Account} fails with them. Give options with them to
   * {@link #create(Vertx, Pool, Driver, OpenFGAServiceGrpcClient, PyriteServiceOptions)}.
   *
   * @param vertx the Vert.x instance, which runs key generation and password hashing on worker threads
   * @param pool the pool of the database that holds the application's tables and Pyrite's
   * @param driver the Cinder driver of the database, such as {@code new PostgresDriver()}
   * @param openFga a client of OpenFGA's API, such as one of Spectrum's {@code OpenFgaService}
   * @return the service, to add to a gRPC server
   */
  static AuthenticationService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga) {
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
  static AuthenticationService create(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteServiceOptions options) {
    return new AuthenticationServiceImpl(vertx, pool, driver, openFga, options);
  }
}
