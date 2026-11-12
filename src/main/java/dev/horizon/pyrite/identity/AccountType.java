package dev.horizon.pyrite.identity;

import com.google.protobuf.Struct;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.OpenFga;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.resource.Resource;
import dev.horizon.pyrite.resource.ResourceTree;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.session.SessionType;
import dev.horizon.pyrite.storage.AccountBackend;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;

/**
 * The declared type {@code pyrite.dev/Account}: it creates accounts as resources that own themselves, finds them,
 * deletes them with what is under them, and authenticates their session tokens. A declaration without the type has
 * none.
 */
public final class AccountType {

  private final ResourceTree resources;
  private final ResourceType declared;
  private final OpenFga openFga;
  private final AccountBackend accounts;
  private final IdentifierType identifiers;
  private final CredentialType credentials;
  private final SessionType sessions;
  private final AccountObserver observer;

  public AccountType(ResourceTree resources, ResourceType declared, OpenFga openFga, Database database,
      IdentifierType identifiers, CredentialType credentials, SessionType sessions, AccountObserver observer) {
    this.resources = resources;
    this.declared = declared;
    this.openFga = openFga;
    this.accounts = new AccountBackend(database);
    this.identifiers = identifiers;
    this.credentials = credentials;
    this.sessions = sessions;
    this.observer = observer;
  }

  /**
   * Creates an account, which owns itself, and tells the handler of created accounts. It checks no relation of the
   * creator, because an account has no parent. When a step after the resource fails, the resource is forgotten again,
   * and the account's row goes with it.
   *
   * @param creator the subject that creates it, which gets the type's {@code creator} relation if it has one
   * @param id the id of the account, such as {@code anne}
   * @param kind {@code HUMAN} or {@code SERVICE}
   * @return the account, or a failure with {@code INVALID_ARGUMENT} for another kind or an id that is not valid, or
   * with {@code ALREADY_EXISTS} when an account has the id
   */
  public Future<Account> create(Subject creator, String id, dev.horizon.pyrite.authentication.v1.Account.Kind kind) {
    if (kind != dev.horizon.pyrite.authentication.v1.Account.Kind.HUMAN
        && kind != dev.horizon.pyrite.authentication.v1.Account.Kind.SERVICE) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "An account's kind is HUMAN or"
          + " SERVICE."));
    }
    return resources.create(creator, declared.resource(id), "", Struct.getDefaultInstance())
        .compose(resource -> accounts.insert(Tables.Account.newBuilder().setName(resource.name())
            .setKind(Tables.Account.Kind.valueOf(kind.name())).build())
            .compose(row -> openFga.write(TupleKey.newBuilder().setUser(resource.object().toString())
                .setRelation(TypeSystem.OWNER).setObject(resource.object().toString()).build())
                .map(ignored -> new Account(this, row)))
            .recover(failure -> resources.forget(resource.name()).transform(ignored -> Future.failedFuture(
                failure))))
        .onSuccess(account -> observer.create(account.message()));
  }

  /**
   * Finds the account of a name.
   *
   * @param name the name, such as {@code accounts/anne}
   * @return the account, or a failure with {@code NOT_FOUND}
   */
  public Future<Account> account(String name) {
    return accounts.findByName(name).map(found -> new Account(this, found.orElseThrow(() -> new PyriteException(
        GrpcStatus.NOT_FOUND, "The account " + name + " does not exist."))));
  }

  /**
   * Finds the account of a subject of the type {@code user}, such as {@code accounts/anne} of {@code user:anne}.
   *
   * @return the account, or a failure with {@code INVALID_ARGUMENT} for a subject of another type, or with
   * {@code NOT_FOUND}
   */
  public Future<Account> account(Subject subject) {
    if (!subject.type().equals(TypeSystem.USER)) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The subject " + subject
          + " is not an account."));
    }
    return account(declared.resource(subject.id()));
  }

  /**
   * Deletes an account, with every grant on it and to it, and tells the handler of deleted accounts. The database
   * deletes its identifiers, credentials, sessions and flows with it, so its sessions end without the handler of
   * ended sessions.
   *
   * @return a future that completes once the account is deleted
   */
  public Future<Void> delete(Account account) {
    return resources.forget(account.name()).onSuccess(ignored -> observer.delete(account.message()));
  }

  /**
   * Deletes the account of a name, such as one that the access service deletes as an object.
   *
   * @return a future that completes once the account is deleted, or a failure with {@code NOT_FOUND}
   */
  public Future<Void> delete(String name) {
    return account(name).compose(this::delete);
  }

  /**
   * Authenticates a session token, and gives the account of its session. A failure to read the account, other than
   * its absence, fails as it is, so that a fault of the database never looks like a session that ended.
   *
   * @param token the token, the id of the session, a dot and its secret
   * @return the principal, with the subject, the account, the session and its expiry, or a failure with
   * {@code UNAUTHENTICATED}
   */
  public Future<Principal> authenticate(String token) {
    return sessions.authenticate(token).compose(session -> account(session.account())
        .recover(failure -> Future.failedFuture(failure instanceof PyriteException refusal
            && refusal.status() == GrpcStatus.NOT_FOUND
                ? new PyriteException(GrpcStatus.UNAUTHENTICATED, "The account of the session is deleted.")
                : failure))
        .map(account -> Principal.newBuilder().setSubject(account.subject()).setAccount(account.name())
            .setSession(session.name()).setExpireTime(session.message().getExpireTime()).build()));
  }

  /**
   * Deletes the rows of credentials and sessions that ended.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return credentials.purge().compose(credentialCount -> sessions.purge().map(sessionCount -> credentialCount
        + sessionCount));
  }

  public IdentifierType identifiers() {
    return identifiers;
  }

  public CredentialType credentials() {
    return credentials;
  }

  public SessionType sessions() {
    return sessions;
  }

  Resource resource(String name) {
    return new Resource(resources, declared, name);
  }
}
