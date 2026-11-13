package dev.horizon.pyrite.session;

import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.DatabaseTime;
import io.vertx.core.Future;
import java.time.Instant;

/**
 * A session of an account, under it: the server's record of one sign-in. Its access tokens authenticate calls, and its
 * refresh tokens issue new ones, until its lifetime ends, its idle lifetime ends without a refresh, or it is ended, on
 * the clock of the database.
 */
public final class Session {

  private final SessionType type;
  private final Tables.Session row;

  Session(SessionType type, Tables.Session row) {
    this.type = type;
    this.row = row;
  }

  public String name() {
    return row.getName();
  }

  public String account() {
    return row.getParent();
  }

  /**
   * Gives the end of the lifetime of the session, which no use extends.
   *
   * @return the time, on the clock of the database
   */
  public Instant expireTime() {
    return DatabaseTime.instant(row.getExpireTime());
  }

  /**
   * Issues tokens of the session: a new access token, and a new refresh token, which Pyrite gives out only this once.
   *
   * @return the tokens, or a failure with {@code NOT_FOUND} when a key that the options name does not exist
   */
  public Future<TokenSet> issueTokens() {
    return type.issueTokens(this);
  }

  /**
   * Ends the session, if it has not ended, and tells the handler of ended sessions; its row stays until a purge.
   *
   * @return a future that completes once the session ended
   */
  public Future<Void> end() {
    return type.end(this);
  }

  /**
   * Gives the session as the authentication service gives it out, without its token.
   *
   * @return the message, with the name, the account and the expiry
   */
  public dev.horizon.pyrite.authentication.v1.Session message() {
    return dev.horizon.pyrite.authentication.v1.Session.newBuilder().setName(name()).setAccount(account())
        .setExpireTime(DatabaseTime.timestamp(row.getExpireTime())).build();
  }

  Tables.Session row() {
    return row;
  }

}
