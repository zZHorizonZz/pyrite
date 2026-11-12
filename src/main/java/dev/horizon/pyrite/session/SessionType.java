package dev.horizon.pyrite.session;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.key.SigningKey;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.SessionBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The sessions of accounts: it issues them, finds them, authenticates their tokens and ends them. A session holds the
 * MAC of the secret of its token, which the MAC key of the options signs, never the secret itself. The database's
 * clock decides when a session expires.
 */
public final class SessionType {

  /**
   * The first segment of the name of a session, such as {@code sessions/3d5e7a1c-0f42-4b8e-9c61-2a7d9e4f1b03}.
   */
  public static final String SESSIONS = "sessions/";

  /**
   * How often an authentication extends the idle lifetime of a session, at most.
   */
  private static final Duration TOUCH = Duration.ofMinutes(1);

  private final SessionBackend sessions;
  private final AuthenticationOptions options;
  private final KeyType keys;
  private final SessionObserver observer;

  public SessionType(Database database, AuthenticationOptions options, KeyType keys,
      SessionObserver observer) {
    this.sessions = new SessionBackend(database);
    this.options = options;
    this.keys = keys;
    this.observer = observer;
  }

  /**
   * Issues a session of an account, which expires after the session lifetime of the options and idles out after
   * their idle lifetime, and tells the handler of issued sessions.
   *
   * @param account the name of the account
   * @return the session, with its token, or a failure with {@code NOT_FOUND} when the MAC key of the options does not
   * exist
   */
  public Future<NewSession> issue(String account) {
    Token token = Token.generate();
    return macKey().compose(key -> key.sign(Session.data(token.secret()))).compose(mac -> sessions.insert(
        Tables.Session.newBuilder().setName(SESSIONS + token.id()).setParent(account).setTokenMac(mac.getSignature())
            .build(),
        Map.of("expire_time", add(now(), options.getSessionLifetime()), "idle_expire_time", add(now(),
            options.getIdleLifetime()))))
        .map(row -> new NewSession(new Session(this, row), token))
        .onSuccess(session -> observer.issue(session.session().message()));
  }

  /**
   * Finds the session of a name, whether it ended or not.
   *
   * @param name the name, such as {@code sessions/3d5e7a1c-0f42-4b8e-9c61-2a7d9e4f1b03}
   * @return the session, or a failure with {@code NOT_FOUND}
   */
  public Future<Session> session(String name) {
    return sessions.findByName(name).map(found -> new Session(this, found.orElseThrow(() -> new PyriteException(
        GrpcStatus.NOT_FOUND, "The session " + name + " does not exist."))));
  }

  /**
   * Authenticates a session token, and extends the idle lifetime of its session, at most once a minute.
   *
   * @param token the token, as the client holds it
   * @return the live session of the token, or a failure with {@code UNAUTHENTICATED}: {@code The session has ended.}
   * when its id names no live session, and {@code The session token is not valid.} when it is malformed or its secret
   * is not the session's
   */
  public Future<Session> authenticate(String token) {
    Token parsed = Token.parse(token);
    return sessions.findLive(SESSIONS + parsed.id()).compose(found -> {
      if (found.isEmpty()) {
        return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has ended."));
      }
      Session session = new Session(this, found.get());
      return macKey().compose(key -> key.verify(Session.data(parsed.secret()), found.get().getTokenMac()
          .toByteArray(), ""))
          .compose(verdict -> verdict.hasVerified()
              ? sessions.touch(found.get().getUid(), options.getIdleLifetime(), TOUCH).map(session)
              : Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session token is not"
                  + " valid.")));
    });
  }

  /**
   * Reads the sessions of an account.
   *
   * @param account the name of the account
   * @return every session whose row is not purged, the ended ones too
   */
  public Future<List<Session>> sessions(String account) {
    return sessions.children(account).map(rows -> rows.stream().map(row -> new Session(this, row)).toList());
  }

  /**
   * Ends the live sessions of an account, and tells the handler of ended sessions of each.
   *
   * @param account the name of the account
   * @return a future that completes once the sessions ended
   */
  public Future<Void> end(String account) {
    return sessions.endLive(account).onSuccess(rows -> rows.forEach(row -> observer.end(new Session(this, row)
        .message()))).mapEmpty();
  }

  /**
   * Deletes the rows of sessions that ended, or that passed their lifetime. A session that idled out stays until the
   * end of its lifetime.
   *
   * @return the number of rows
   */
  public Future<Long> purge() {
    return sessions.purge();
  }

  /**
   * Ends a session, if it has not ended.
   */
  Future<Void> end(Session session) {
    return sessions.end(session.row().getUid()).onSuccess(ended -> ended.ifPresent(row -> observer.end(
        new Session(this, row).message()))).mapEmpty();
  }

  private Future<SigningKey> macKey() {
    return keys.signing(options.getMacKey());
  }
}
