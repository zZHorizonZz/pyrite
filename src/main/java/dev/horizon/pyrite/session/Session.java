package dev.horizon.pyrite.session;

import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.DatabaseTime;
import io.vertx.core.Future;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * A session of an account, under it, which a session token authenticates until its lifetime ends, its idle lifetime
 * ends without a use, or it is ended, on the clock of the database.
 */
public final class Session {

  private final SessionType type;
  private final Tables.Session row;

  Session(SessionType type, Tables.Session row) {
    this.type = type;
    this.row = row;
  }

  /**
   * Gives the data whose MAC a session holds in place of the secret of its token.
   */
  static byte[] data(String secret) {
    return ("session\0" + secret).getBytes(StandardCharsets.UTF_8);
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
