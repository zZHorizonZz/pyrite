package dev.horizon.pyrite.identity;

import com.google.protobuf.Struct;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.access.OpenFga;
import dev.horizon.pyrite.declaration.ResourceType;
import dev.horizon.pyrite.declaration.TypeSystem;
import dev.horizon.pyrite.observer.LifecycleObserver;
import dev.horizon.pyrite.resource.Resource;
import dev.horizon.pyrite.resource.ResourceTree;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.AccountBackend;
import dev.horizon.pyrite.storage.DatabaseTime;
import dev.horizon.spectrum.openfga.v1.TupleKey;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;

/**
 * The accounts of the declared type {@code pyrite.dev/Account}: a person or a service, with its identifiers, its
 * factors and its sessions under it. It creates accounts as resources that own themselves, finds them, and deletes
 * them with what is under them. A declaration without the type has none.
 */
public final class AccountService {

  private final ResourceTree resources;
  private final ResourceType declared;
  private final OpenFga openFga;
  private final AccountBackend accounts;
  private final LifecycleObserver<dev.horizon.pyrite.authentication.v1.Account> observer;

  public AccountService(ResourceTree resources, ResourceType declared, OpenFga openFga, Database database,
      LifecycleObserver<dev.horizon.pyrite.authentication.v1.Account> observer) {
    this.resources = resources;
    this.declared = declared;
    this.openFga = openFga;
    this.accounts = new AccountBackend(database);
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
  public Future<Tables.Account> create(Subject creator, String id,
      dev.horizon.pyrite.authentication.v1.Account.Kind kind) {
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
                .map(ignored -> row))
            .recover(failure -> resources.forget(resource.name()).transform(ignored -> Future.failedFuture(
                failure))))
        .onSuccess(row -> observer.begin(message(row)));
  }

  /**
   * Finds the account of a name.
   *
   * @param name the name, such as {@code accounts/anne}
   * @return the account, or a failure with {@code NOT_FOUND}
   */
  public Future<Tables.Account> account(String name) {
    return accounts.findByName(name).map(found -> found.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
        "The account " + name + " does not exist.")));
  }

  /**
   * Finds the account of a subject of the type {@code user}, such as {@code accounts/anne} of {@code user:anne}.
   *
   * @return the account, or a failure with {@code INVALID_ARGUMENT} for a subject of another type, or with
   * {@code NOT_FOUND}
   */
  public Future<Tables.Account> account(Subject subject) {
    if (!subject.type().equals(TypeSystem.USER)) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The subject " + subject
          + " is not an account."));
    }
    return account(declared.resource(subject.id()));
  }

  /**
   * Finds the account of a name for a subject that has a relation on it. The relation is checked first, on the name
   * alone, so a subject without it learns nothing of whether the account exists.
   *
   * @param name the name, such as {@code accounts/anne}
   * @param subject the subject that calls
   * @param relation the relation that the call needs, such as {@code viewer}
   * @return the account, or a failure with {@code PERMISSION_DENIED} when the subject lacks the relation, whether the
   * account exists or not; with {@code INVALID_ARGUMENT} for a name that is not of an account; or with
   * {@code NOT_FOUND}
   */
  public Future<Tables.Account> account(String name, Subject subject, String relation) {
    if (!name.startsWith(declared.plural() + "/")) {
      return Future.failedFuture(new PyriteException(GrpcStatus.INVALID_ARGUMENT, "The name " + name
          + " is not of an account."));
    }
    return new Resource(resources, declared, name).require(subject, relation, Struct.getDefaultInstance())
        .compose(allowed -> account(name));
  }

  /**
   * Deletes the account of a name, with every grant on it and to it, and tells the handler of deleted accounts. The
   * database deletes its identifiers, factors, sessions and exchanges with it, so its sessions end without the handler
   * of ended sessions.
   *
   * @return a future that completes once the account is deleted, or a failure with {@code NOT_FOUND}
   */
  public Future<Void> delete(String name) {
    return account(name).compose(row -> resources.forget(row.getName())
        .onSuccess(ignored -> observer.end(message(row))));
  }

  /**
   * Gives an account as the authentication service gives it out.
   *
   * @return the message, with the name, the kind, and the time that the database created it
   */
  public static dev.horizon.pyrite.authentication.v1.Account message(Tables.Account row) {
    return dev.horizon.pyrite.authentication.v1.Account.newBuilder().setName(row.getName())
        .setKind(dev.horizon.pyrite.authentication.v1.Account.Kind.valueOf(row.getKind().name()))
        .setCreateTime(DatabaseTime.timestamp(row.getCreateTime()))
        .build();
  }

  /**
   * Gives the subject that an account is in OpenFGA, and in the requests of the services.
   *
   * @param account the name of the account
   * @return the subject, such as {@code user:anne} for {@code accounts/anne}
   */
  public static String subject(String account) {
    return Subject.account(account).toString();
  }
}
