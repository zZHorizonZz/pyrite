package dev.horizon.pyrite;

import dev.horizon.cinder.Driver;
import dev.horizon.pyrite.access.AccessEndpoint;
import dev.horizon.pyrite.authentication.v1.Account;
import dev.horizon.pyrite.authentication.v1.Session;
import dev.horizon.pyrite.declaration.SchemaEndpoint;
import dev.horizon.pyrite.deployment.Deployment;
import dev.horizon.pyrite.exchange.ExchangeInterceptor;
import dev.horizon.pyrite.identity.AuthenticationEndpoint;
import dev.horizon.pyrite.key.CryptographyEndpoint;
import dev.horizon.pyrite.observer.LifecycleObserver;
import dev.horizon.spectrum.openfga.v1.OpenFGAServiceGrpcClient;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.grpc.server.Service;
import io.vertx.grpc.server.ServiceContainer;
import io.vertx.sqlclient.Pool;
import java.util.Objects;

/**
 * Pyrite in an application: its four gRPC services, schema, access, cryptography and authentication, over Pyrite's
 * tables in a SQL database and an OpenFGA store. The services share the latest declaration, which the schema service
 * writes and every call reads.
 *
 * <pre>{@code
 * RelyingPartyOptions relyingParty = new RelyingPartyOptions().setId("acme.dev").setName("Acme")
 *     .addOrigin("https://acme.dev");
 * AuthenticationOptions authentication = new AuthenticationOptions().setMacKey("keys/sessions")
 *     .setEncryptionKey("keys/factors").setAccessTokenKey("keys/access").setIssuer("Acme")
 *     .setRelyingPartyOptions(relyingParty);
 * PyriteOptions options = new PyriteOptions().setKeyOptions(new KeyOptions().setRoot(root))
 *     .setAuthenticationOptions(authentication);
 *
 * Pyrite pyrite = Pyrite.pyrite(vertx, pool, new PostgresDriver(), openFga, options).bind(server);
 * }</pre>
 *
 * <p>
 * Each call gives its result or its failure through a future, and a call that Pyrite refuses ends with the gRPC
 * status of its {@link PyriteException}. The database is the clock of every time that Pyrite stores, such as the
 * expiry of a session, so that every Pyrite on the same database agrees on it.
 *
 * <p>
 * The interceptors that an application adds here take part in the exchanges of the authentication service, and the
 * handlers hear of the accounts and sessions that it creates and ends. The handlers set on one Pyrite hear only of
 * what that Pyrite does.
 */
public final class Pyrite {

  private final ExchangeInterceptor interceptor = new ExchangeInterceptor();
  private final LifecycleObserver<dev.horizon.pyrite.authentication.v1.Account> accountObserver = new LifecycleObserver<>(
      "accounts");
  private final LifecycleObserver<dev.horizon.pyrite.authentication.v1.Session> sessionObserver = new LifecycleObserver<>(
      "sessions");
  private final Service access;
  private final Service authentication;
  private final Service cryptography;
  private final Service schema;
  private final Deployment deployment;

  private Pyrite(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteOptions options) {
    this.deployment = new Deployment(vertx, pool, driver, openFga, options, interceptor, accountObserver,
        sessionObserver);
    this.access = new AccessEndpoint(deployment);
    this.authentication = new AuthenticationEndpoint(deployment);
    this.cryptography = new CryptographyEndpoint(deployment);
    this.schema = new SchemaEndpoint(deployment);
  }

  /**
   * Starts Pyrite with the default options: no root key and no authentication options, so its declaration can have
   * neither {@code pyrite.dev/Key} nor {@code pyrite.dev/Account}, and a random page token key.
   *
   * @param vertx the Vert.x of the application
   * @param pool the pool of the database of Pyrite's tables
   * @param driver the Cinder driver of the database, such as {@code new PostgresDriver()}
   * @param openFga the client of the OpenFGA service that keeps the grants
   * @return Pyrite, whose services the application adds to its gRPC server
   */
  public static Pyrite pyrite(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga) {
    return pyrite(vertx, pool, driver, openFga, new PyriteOptions());
  }

  /**
   * Starts Pyrite. It copies the options, so a later change of them does not reach it.
   *
   * @param vertx the Vert.x of the application
   * @param pool the pool of the database of Pyrite's tables
   * @param driver the Cinder driver of the database, such as {@code new PostgresDriver()}
   * @param openFga the client of the OpenFGA service that keeps the grants
   * @param options the options, such as the root key of keys and the options of authentication; every Pyrite on the
   * same database needs the same ones
   * @return Pyrite, whose services the application adds to its gRPC server
   */
  public static Pyrite pyrite(Vertx vertx, Pool pool, Driver driver, OpenFGAServiceGrpcClient openFga,
      PyriteOptions options) {
    return new Pyrite(vertx, pool, driver, openFga, new PyriteOptions(options));
  }

  /**
   * Deletes the rows that no call needs any more: the sessions that ended or passed their lifetime, with their refresh
   * tokens; the factors that were removed or replaced, or whose enrolment no exchange finished; and the exchanges that
   * expired, with the responses that they keep. Run it now and then, such as from a timer. It is no call of the
   * services, so no client can make it.
   *
   * @return the number of rows, or a failure when the declaration has no accounts
   */
  public Future<Long> purge() {
    return deployment.schema().compose(schema -> schema.factors().purge().compose(factors -> schema.sessions().purge()
        .compose(sessions -> schema.exchanges().purge().map(exchanges -> factors + sessions + exchanges))));
  }

  /**
   * Adds every service of Pyrite to a gRPC server. To serve only some of them, add those that {@link #access()},
   * {@link #authentication()}, {@link #cryptography()} and {@link #schema()} give.
   *
   * @param server the server, such as a {@code GrpcServer} or an {@code EventBusGrpcServer}
   * @return this Pyrite
   */
  public Pyrite bind(ServiceContainer server) {
    server.addService(access).addService(authentication).addService(cryptography).addService(schema);
    return this;
  }

  /**
   * Gives the service {@code pyrite.access.v1.Access}: the resources that Pyrite knows, the grants on them, and the
   * checks and lists of who can do what.
   *
   * @return the service
   */
  public Service access() {
    return access;
  }

  /**
   * Gives the service {@code pyrite.authentication.v1.Authentication}: accounts, their identifiers and sessions, and
   * exchanges. It needs a declaration with {@code pyrite.dev/Account}.
   *
   * @return the service
   */
  public Service authentication() {
    return authentication;
  }

  /**
   * Gives the service {@code pyrite.crypto.v1.Cryptography}: keys, encryption and signatures. It needs a declaration
   * with {@code pyrite.dev/Key}.
   *
   * @return the service
   */
  public Service cryptography() {
    return cryptography;
  }

  /**
   * Gives the service {@code pyrite.schema.v1.Schema}, which writes and reads the declarations. It takes no subject,
   * so serve it only to the application.
   *
   * @return the service
   */
  public Service schema() {
    return schema;
  }

  /**
   * Adds an interceptor of the start of exchanges, which runs before an exchange takes its first answer and can refuse
   * it. The interceptors of a moment run in the order that they were added.
   *
   * @param interceptor the interceptor, which calls {@code next()} or {@code fail(cause)} on the context
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite addExchangeStartInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.addStart(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Removes an interceptor of the start of exchanges.
   *
   * @param interceptor the interceptor, as it was added
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite removeExchangeStartInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.removeStart(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Adds an interceptor of the stages of exchanges, which runs each time that an answer meets a stage, before the next
   * stage.
   *
   * @param interceptor the interceptor, which calls {@code next()} or {@code fail(cause)} on the context
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite addStageEndInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.addStageEnd(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Removes an interceptor of the stages of exchanges.
   *
   * @param interceptor the interceptor, as it was added
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite removeStageEndInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.removeStageEnd(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Adds an interceptor of the end of exchanges, which runs after the interceptors added before it, when the last stage
   * is met. The interceptors of the end run before what the declaration of the exchange completes with, such as issuing
   * a session, so one that fails stops the exchange before anything changes.
   *
   * @param interceptor the interceptor, which calls {@code next()} or {@code fail(cause)} on the context
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite addExchangeEndInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.addEnd(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Removes an interceptor of the end of exchanges.
   *
   * @param interceptor the interceptor, as it was added
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite removeExchangeEndInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.removeEnd(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Adds an interceptor of the failures of exchanges, which runs after a wrong answer or a refused one, before the call
   * fails. It can fail the call with another cause.
   *
   * @param interceptor the interceptor, which calls {@code next()} or {@code fail(cause)} on the context
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite addExchangeFailureInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.addFailure(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Removes an interceptor of the failures of exchanges.
   *
   * @param interceptor the interceptor, as it was added
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite removeExchangeFailureInterceptor(Handler<ExchangeContext> interceptor) {
    this.interceptor.removeFailure(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Adds an interceptor that delivers one-time codes. With none, the response of an exchange carries the code, for the
   * application to deliver; with one, the interceptors deliver it, and the response never carries it.
   *
   * @param interceptor the interceptor, which calls {@code next()}, or {@code fail(cause)} on the context to stop the
   * exchange, such as when it cannot send the code
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite addCodeInterceptor(Handler<CodeContext> interceptor) {
    this.interceptor.addCode(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Removes an interceptor that delivers one-time codes. Without any, the responses of exchanges carry the codes again.
   *
   * @param interceptor the interceptor, as it was added
   * @return this Pyrite
   * @throws NullPointerException if the interceptor is {@code null}
   */
  public Pyrite removeCodeInterceptor(Handler<CodeContext> interceptor) {
    this.interceptor.removeCode(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  /**
   * Sets the handler of the accounts that Pyrite creates, which hears of each after the fact. A handler that throws
   * changes nothing of what happened.
   *
   * @param handler the handler, in place of the one before
   * @return this Pyrite
   * @throws NullPointerException if the handler is {@code null}
   */
  public Pyrite accountCreateHandler(Handler<Account> handler) {
    accountObserver.beginHandler(Objects.requireNonNull(handler, "handler"));
    return this;
  }

  /**
   * Sets the handler of the accounts that Pyrite deletes, through the authentication service or as objects of the
   * access service, which hears of each after the fact. A handler that throws changes nothing of what happened.
   *
   * @param handler the handler, in place of the one before
   * @return this Pyrite
   * @throws NullPointerException if the handler is {@code null}
   */
  public Pyrite accountDeleteHandler(Handler<Account> handler) {
    accountObserver.endHandler(Objects.requireNonNull(handler, "handler"));
    return this;
  }

  /**
   * Sets the handler of the sessions that Pyrite issues, which hears of each after the fact. A handler that throws
   * changes nothing of what happened.
   *
   * @param handler the handler, in place of the one before
   * @return this Pyrite
   * @throws NullPointerException if the handler is {@code null}
   */
  public Pyrite sessionIssueHandler(Handler<Session> handler) {
    sessionObserver.beginHandler(Objects.requireNonNull(handler, "handler"));
    return this;
  }

  /**
   * Sets the handler of the sessions that Pyrite ends, which hears of each after the fact. A session that expires, or
   * whose account is deleted, ends without it. A handler that throws changes nothing of what happened.
   *
   * @param handler the handler, in place of the one before
   * @return this Pyrite
   * @throws NullPointerException if the handler is {@code null}
   */
  public Pyrite sessionEndHandler(Handler<Session> handler) {
    sessionObserver.endHandler(Objects.requireNonNull(handler, "handler"));
    return this;
  }
}
