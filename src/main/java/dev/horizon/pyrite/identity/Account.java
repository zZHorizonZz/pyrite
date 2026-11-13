package dev.horizon.pyrite.identity;

import dev.horizon.pyrite.resource.Resource;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.session.Session;
import dev.horizon.pyrite.storage.DatabaseTime;
import io.vertx.core.Future;
import java.util.List;

/**
 * An account, the resource {@code pyrite.dev/Account}: a person or a service, with its identifiers, its credentials
 * and its sessions under it. {@link AccountType} creates and finds accounts; an account reads what it holds, and issues
 * and ends its sessions.
 */
public final class Account {

  private final AccountType type;
  private final Tables.Account row;

  Account(AccountType type, Tables.Account row) {
    this.type = type;
    this.row = row;
  }

  public String name() {
    return row.getName();
  }

  /**
   * Gives the subject that the account is in OpenFGA, and in the requests of the services.
   *
   * @return the subject, such as {@code user:anne} for {@code accounts/anne}
   */
  public String subject() {
    return Subject.account(name()).toString();
  }

  /**
   * Gives the account as a resource, on which a call checks the relation that it needs, such as {@code viewer}.
   *
   * @return the resource
   */
  public Resource resource() {
    return type.resource(name());
  }

  /**
   * Reads the identifiers of the account.
   *
   * @return the identifiers, verified or not
   */
  public Future<List<Identifier>> identifiers() {
    return type.identifiers().identifiers(name());
  }

  /**
   * Reads the credentials of the account.
   *
   * @return every credential, the pending and the removed ones too; {@link Credential#isActive()} tells which count
   */
  public Future<List<Credential<?>>> credentials() {
    return type.credentials().credentials(name());
  }

  /**
   * Issues a session of the account, without tokens; {@link Session#issueTokens()} gives them.
   *
   * @return the session
   */
  public Future<Session> issueSession() {
    return type.sessions().issue(name());
  }

  /**
   * Ends every live session of the account, and tells the handler of ended sessions of each.
   *
   * @return a future that completes once the sessions ended
   */
  public Future<Void> endSessions() {
    return type.sessions().end(name());
  }

  /**
   * Gives the account as the authentication service gives it out.
   *
   * @return the message, with the name, the kind, and the time that the database created it
   */
  public dev.horizon.pyrite.authentication.v1.Account message() {
    return dev.horizon.pyrite.authentication.v1.Account.newBuilder().setName(name())
        .setKind(dev.horizon.pyrite.authentication.v1.Account.Kind.valueOf(row.getKind().name()))
        .setCreateTime(DatabaseTime.timestamp(row.getCreateTime()))
        .build();
  }
}
