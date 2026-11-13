package dev.horizon.pyrite.deployment;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteOptions;
import dev.horizon.pyrite.access.OpenFga;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.exchange.ExchangeInterceptor;
import dev.horizon.pyrite.exchange.ExchangeService;
import dev.horizon.pyrite.identity.AccountService;
import dev.horizon.pyrite.identity.FactorService;
import dev.horizon.pyrite.identity.IdentifierService;
import dev.horizon.pyrite.key.KeyService;
import dev.horizon.pyrite.observer.LifecycleObserver;
import dev.horizon.pyrite.resource.ResourceTree;
import dev.horizon.pyrite.session.SessionService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;

/**
 * A declaration bound to its database and its store of grants: its types, the tree of the resources that Pyrite knows,
 * and Pyrite's own keys, accounts and exchanges.
 *
 * <p>
 * A schema is what a call works on. The application keeps its resources itself; the tree knows each one's type and
 * parent, so that a grant on a parent reaches it.
 */
public final class Schema {

  private final TypeSystem types;
  private final ResourceTree resources;
  private final PyriteOptions options;
  private final Optional<KeyService> keys;
  private final Optional<AccountService> accounts;
  private final Optional<IdentifierService> identifiers;
  private final Optional<FactorService> factors;
  private final Optional<SessionService> sessions;
  private final Optional<ExchangeService> exchanges;

  Schema(TypeSystem types, Database database, OpenFga openFga, Vertx vertx, PyriteOptions options,
      ExchangeInterceptor interceptor, LifecycleObserver<dev.horizon.pyrite.authentication.v1.Account> accountObserver,
      LifecycleObserver<dev.horizon.pyrite.authentication.v1.Session> sessionObserver) {
    this.types = types;
    this.resources = new ResourceTree(types, openFga, database);
    this.options = options;
    this.keys = types.type(TypeSystem.KEY).map(type -> new KeyService(resources, type, database, vertx,
        options.getKeyOptions()));
    AuthenticationOptions authentication = options.getAuthenticationOptions();
    this.accounts = types.type(TypeSystem.ACCOUNT).map(type -> new AccountService(resources, type, openFga, database,
        accountObserver));
    this.identifiers = accounts.map(ignored -> new IdentifierService(database, types));
    this.factors = accounts.map(ignored -> new FactorService(database, vertx, authentication, keys()));
    this.sessions = accounts.map(service -> new SessionService(database, authentication, keys(), service,
        sessionObserver));
    this.exchanges = accounts.map(service -> new ExchangeService(database, types, authentication, service,
        identifiers.get(), factors.get(), sessions.get(), keys(), interceptor));
  }

  public TypeSystem types() {
    return types;
  }

  public ResourceTree resources() {
    return resources;
  }

  public OpenFga openFga() {
    return resources.openFga();
  }

  public PyriteOptions options() {
    return options;
  }

  /**
   * Gives a declared type.
   *
   * @param name the name of the type, such as {@code acme.dev/Project}
   * @return the type, or a failure with {@code INVALID_ARGUMENT} when the declaration has none of the name
   */
  public Future<ResourceType> type(String name) {
    return types.type(name).map(Future::succeededFuture).orElseGet(() -> Future.failedFuture(new PyriteException(
        GrpcStatus.INVALID_ARGUMENT, "The type " + name + " is not declared.")));
  }

  /**
   * Gives the keys of the declaration.
   *
   * @throws PyriteException with {@code FAILED_PRECONDITION} when the declaration has no {@code pyrite.dev/Key}
   */
  public KeyService keys() {
    return keys.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " keys; declare the type " + TypeSystem.KEY + "."));
  }

  /**
   * Gives the accounts of the declaration.
   *
   * @throws PyriteException with {@code FAILED_PRECONDITION} when the declaration has no {@code pyrite.dev/Account}
   */
  public AccountService accounts() {
    return withAccounts(accounts);
  }

  /**
   * Gives the identifiers of the accounts, which a declaration with accounts has.
   */
  public IdentifierService identifiers() {
    return withAccounts(identifiers);
  }

  /**
   * Gives the factors of the accounts, which a declaration with accounts has.
   */
  public FactorService factors() {
    return withAccounts(factors);
  }

  /**
   * Gives the sessions of the accounts, which a declaration with accounts has.
   */
  public SessionService sessions() {
    return withAccounts(sessions);
  }

  /**
   * Gives the exchanges of the accounts, which a declaration with accounts has.
   */
  public ExchangeService exchanges() {
    return withAccounts(exchanges);
  }

  /**
   * Forgets a resource: an account through its type, which tells the handlers of deleted accounts, and any other
   * resource through the tree.
   *
   * @param name the name of the resource, such as {@code projects/ledger}
   * @return a future that completes once the resource is forgotten, or a failure with {@code FAILED_PRECONDITION}
   * when resources are under it
   */
  public Future<Void> forget(String name) {
    if (accounts.isPresent() && types.typeOf(name).map(type -> type.name().equals(TypeSystem.ACCOUNT)).orElse(false)) {
      return accounts.get().delete(name);
    }
    return resources.forget(name);
  }

  private static <T> T withAccounts(Optional<T> service) {
    return service.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " accounts; declare the type " + TypeSystem.ACCOUNT + "."));
  }
}
