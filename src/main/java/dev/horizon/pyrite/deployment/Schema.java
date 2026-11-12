package dev.horizon.pyrite.deployment;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.PyriteOptions;
import dev.horizon.pyrite.access.OpenFga;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.flow.FlowInterceptor;
import dev.horizon.pyrite.flow.FlowType;
import dev.horizon.pyrite.identity.AccountObserver;
import dev.horizon.pyrite.identity.AccountType;
import dev.horizon.pyrite.identity.CredentialType;
import dev.horizon.pyrite.identity.IdentifierType;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.key.RootKey;
import dev.horizon.pyrite.resource.ResourceTree;
import dev.horizon.pyrite.session.SessionObserver;
import dev.horizon.pyrite.session.SessionType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.grpc.common.GrpcStatus;
import java.util.Optional;

/**
 * A declaration bound to its database and its OpenFGA store: its types, the tree of the resources that Pyrite knows,
 * and Pyrite's own keys, accounts and flows.
 *
 * <p>
 * A schema is what a call works on. The application keeps its resources itself; the tree knows each one's type and
 * parent, so that a grant on a parent reaches it.
 */
public final class Schema {

  private final TypeSystem types;
  private final ResourceTree resources;
  private final PyriteOptions options;
  private final Optional<KeyType> keys;
  private final Optional<AccountType> accounts;
  private final Optional<FlowType> flows;

  Schema(TypeSystem types, Database database, OpenFga openFga, Vertx vertx, PyriteOptions options,
      FlowInterceptor interceptor, AccountObserver accountObserver, SessionObserver sessionObserver) {
    this.types = types;
    this.resources = new ResourceTree(types, openFga, database);
    this.options = options;
    this.keys = types.type(TypeSystem.KEY).map(declared -> new KeyType(resources, declared, database, vertx,
        options.getKeyOptions(), new RootKey(Optional.ofNullable(options.getKeyOptions().getRoot()).orElseThrow(
            () -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has keys, whose material a"
                + " root key seals, but the options name no root key.")))));
    this.accounts = types.type(TypeSystem.ACCOUNT).map(declared -> {
      AuthenticationOptions authentication = options.getAuthenticationOptions();
      return new AccountType(resources, declared, openFga, database, new IdentifierType(database, types),
          new CredentialType(database, vertx, authentication, keys()),
          new SessionType(database, authentication, keys(), sessionObserver), accountObserver);
    });
    this.flows = accounts.map(accountType -> new FlowType(database, types, vertx,
        options.getAuthenticationOptions(), accountType, keys(), interceptor));
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
   * @return the type {@code pyrite.dev/Key}
   * @throws PyriteException with {@code FAILED_PRECONDITION} when the declaration has no {@code pyrite.dev/Key}
   */
  public KeyType keys() {
    return keys.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " keys; declare the type " + TypeSystem.KEY + "."));
  }

  /**
   * Gives the accounts of the declaration.
   *
   * @return the type {@code pyrite.dev/Account}
   * @throws PyriteException with {@code FAILED_PRECONDITION} when the declaration has no {@code pyrite.dev/Account}
   */
  public AccountType accounts() {
    return accounts.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " accounts; declare the type " + TypeSystem.ACCOUNT + "."));
  }

  /**
   * Gives the flows of the declaration.
   *
   * @return the flows
   * @throws PyriteException with {@code FAILED_PRECONDITION} when the declaration has no {@code pyrite.dev/Account}
   */
  public FlowType flows() {
    return flows.orElseThrow(() -> new PyriteException(GrpcStatus.FAILED_PRECONDITION, "The declaration has no"
        + " accounts, so it has no flows."));
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
}
