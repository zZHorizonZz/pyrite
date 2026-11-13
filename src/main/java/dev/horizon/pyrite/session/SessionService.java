package dev.horizon.pyrite.session;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.identity.AccountService;
import dev.horizon.pyrite.key.KeyService;
import dev.horizon.pyrite.observer.LifecycleObserver;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.DatabaseTime;
import dev.horizon.pyrite.storage.RefreshTokenBackend;
import dev.horizon.pyrite.storage.SessionBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The sessions of accounts, each under its account: the server's record of one sign-in. It issues them and their
 * tokens, authenticates access tokens, trades refresh tokens for new ones, and ends sessions. A session works until its
 * lifetime ends, its idle lifetime ends without a refresh, or it is ended, on the clock of the database.
 *
 * <p>
 * An access token is a JWT of type {@code at+jwt}, which the access token key of the options signs, with the claims
 * {@code iss}, {@code sub}, {@code sid}, {@code iat}, {@code exp} and {@code jti}. A service verifies it with the key's
 * public key, without a call to Pyrite, and takes it until it expires, even after its session ends;
 * {@code Authenticate} also checks that the session has not ended.
 *
 * <p>
 * A refresh token is opaque, and works once: a refresh trades it for the next, and extends the idle lifetime of the
 * session. A token that comes back after its trade was copied, so its session ends. Its row keeps only the MAC of its
 * secret, which the MAC key of the options signs.
 */
public final class SessionService {

  private static final String SESSIONS = "sessions/";
  private static final String ACCESS_TOKEN_TYPE = "at+jwt";

  /**
   * How often a refresh extends the idle lifetime of a session, at most.
   */
  private static final Duration TOUCH = Duration.ofMinutes(1);

  private final SessionBackend sessions;
  private final RefreshTokenBackend refreshTokens;
  private final AuthenticationOptions options;
  private final KeyService keys;
  private final AccountService accounts;
  private final LifecycleObserver<dev.horizon.pyrite.authentication.v1.Session> observer;

  public SessionService(Database database, AuthenticationOptions options, KeyService keys, AccountService accounts,
      LifecycleObserver<dev.horizon.pyrite.authentication.v1.Session> observer) {
    this.sessions = new SessionBackend(database);
    this.refreshTokens = new RefreshTokenBackend(database);
    this.options = options;
    this.keys = keys;
    this.accounts = accounts;
    this.observer = observer;
  }

  /**
   * Issues a session of an account, which expires after the session lifetime of the options and idles out after
   * their idle lifetime, and tells the handler of issued sessions. The session has no tokens yet;
   * {@link #issueTokens(Tables.Session)} gives them.
   *
   * @param account the name of the account
   * @return the session
   */
  public Future<Tables.Session> issue(String account) {
    return sessions.insert(Tables.Session.newBuilder().setName(SESSIONS + UUID.randomUUID()).setParent(account)
        .build(),
        Map.of("expire_time", add(now(), options.getSessionLifetime()), "idle_expire_time", add(now(),
            options.getIdleLifetime())))
        .onSuccess(row -> observer.begin(message(row)));
  }

  /**
   * Issues tokens of a session: a new access token, and a new refresh token, which Pyrite gives out only this once.
   *
   * @return the tokens, or a failure with {@code NOT_FOUND} when a key that the options name does not exist
   */
  public Future<TokenSet> issueTokens(Tables.Session session) {
    return sign(session).compose(issue -> refreshTokens.insert(issue.refreshRow()).map(issue.tokens()));
  }

  /**
   * Finds the session of a name, whether it ended or not.
   *
   * @param name the name, such as {@code sessions/3d5e7a1c-0f42-4b8e-9c61-2a7d9e4f1b03}
   * @return the session, or a failure with {@code NOT_FOUND}
   */
  public Future<Tables.Session> session(String name) {
    return sessions.findByName(name).map(found -> found.orElseThrow(() -> new PyriteException(GrpcStatus.NOT_FOUND,
        "The session " + name + " does not exist.")));
  }

  /**
   * Authenticates an access token: its signature, its type, its issuer and its expiry, that its session has not ended,
   * and that its account exists. A failure to read the account, other than its absence, fails as it is, so that a fault
   * of the database never looks like a session that ended.
   *
   * @param accessToken the token, as the client holds it
   * @return the principal, with the subject, the account, the session and its expiry, or a failure with
   * {@code UNAUTHENTICATED}: {@code The access token is not valid: ...} with the reason,
   * {@code The session has ended.}, or {@code The account of the session is deleted.}
   */
  public Future<Principal> authenticate(String accessToken) {
    return liveSession(accessToken).compose(session -> accounts.account(session.getParent())
        .recover(failure -> Future.failedFuture(failure instanceof PyriteException refusal
            && refusal.status() == GrpcStatus.NOT_FOUND
                ? new PyriteException(GrpcStatus.UNAUTHENTICATED, "The account of the session is deleted.")
                : failure))
        .map(account -> Principal.newBuilder().setSubject(AccountService.subject(account.getName()))
            .setAccount(account.getName()).setSession(session.getName())
            .setExpireTime(DatabaseTime.timestamp(session.getExpireTime())).build()));
  }

  /**
   * Trades a refresh token for new tokens of its session, and extends the idle lifetime of the session, at most once
   * a minute. A token that a refresh traded before ends its session.
   *
   * @param refreshToken the refresh token, as the last tokens gave it
   * @return the new tokens, or a failure with {@code UNAUTHENTICATED}: {@code The refresh token is not valid.} when it
   * is malformed or its secret is not the token's, {@code The session has ended.}, or {@code The refresh token was
   * used before, so its session has ended.}
   */
  public Future<TokenSet> refresh(String refreshToken) {
    Token parsed;
    try {
      parsed = Token.parse(refreshToken);
    } catch (PyriteException malformed) {
      return Future.failedFuture(malformed);
    }
    return refreshTokens.findById(parsed.id()).compose(found -> found.isEmpty() ? invalid()
        : keys.verify(options.getMacKey(), data(parsed.secret()), found.get().getMac().toByteArray(), "")
            .compose(verdict -> verdict.hasVerified() ? trade(found.get()) : invalid()));
  }

  /**
   * Ends a session, if it has not ended, and tells the handler of ended sessions; its row stays until a purge.
   *
   * @return a future that completes once the session ended
   */
  public Future<Void> end(Tables.Session session) {
    return sessions.end(session.getUid()).onSuccess(row -> row.ifPresent(ending -> observer.end(message(ending))))
        .mapEmpty();
  }

  /**
   * Ends the live sessions of an account, and tells the handler of ended sessions of each.
   *
   * @param account the name of the account
   * @return a future that completes once the sessions ended
   */
  public Future<Void> endAll(String account) {
    return sessions.endLive(account).onSuccess(rows -> rows.forEach(row -> observer.end(message(row)))).mapEmpty();
  }

  /**
   * Deletes the rows of sessions that ended, or that passed their lifetime, with their refresh tokens. A session that
   * idled out stays until the end of its lifetime.
   *
   * @return the number of rows of sessions
   */
  public Future<Long> purge() {
    return sessions.purge();
  }

  /**
   * Gives a session as the authentication service gives it out, without its tokens.
   *
   * @return the message, with the name, the account and the expiry
   */
  public static dev.horizon.pyrite.authentication.v1.Session message(Tables.Session row) {
    return dev.horizon.pyrite.authentication.v1.Session.newBuilder().setName(row.getName()).setAccount(row.getParent())
        .setExpireTime(DatabaseTime.timestamp(row.getExpireTime())).build();
  }

  /**
   * Gives the live session of an access token whose signature, type, issuer and expiry verify.
   */
  private Future<Tables.Session> liveSession(String accessToken) {
    return keys.verifyJwt(options.getAccessTokenKey(), accessToken, Expected.newBuilder().setType(ACCESS_TOKEN_TYPE)
        .setIssuer(options.getIssuer()).build()).compose(verdict -> {
          if (verdict.hasRejected()) {
            return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The access token is not"
                + " valid: " + verdict.getRejected().getMessage()));
          }
          String session = verdict.getVerified().getClaims().getFieldsOrDefault("sid", Value.getDefaultInstance())
              .getStringValue();
          return sessions.findLive(session).compose(found -> found.isPresent() ? Future.succeededFuture(found.get())
              : Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has ended.")));
        });
  }

  /**
   * The tokens of one issue, signed, with the row of the refresh token that the database still has to take.
   */
  private record TokenIssue(TokenSet tokens, Tables.RefreshToken refreshRow) {
  }

  /**
   * Signs new tokens of a session, writing nothing: the MAC of the refresh token's secret, and the access token, whose
   * {@code exp} is its lifetime from now, or the end of the session's lifetime when that comes first.
   */
  private Future<TokenIssue> sign(Tables.Session session) {
    Token refresh = Token.generate();
    Instant issueTime = Instant.now();
    Instant lifetimeEnd = issueTime.plus(options.getAccessTokenLifetime());
    Instant sessionEnd = DatabaseTime.instant(session.getExpireTime());
    Instant expiry = lifetimeEnd.isBefore(sessionEnd) ? lifetimeEnd : sessionEnd;
    Struct claims = Struct.newBuilder()
        .putFields("iss", text(options.getIssuer()))
        .putFields("sub", text(AccountService.subject(session.getParent())))
        .putFields("sid", text(session.getName()))
        .putFields("iat", Value.newBuilder().setNumberValue(issueTime.getEpochSecond()).build())
        .putFields("exp", Value.newBuilder().setNumberValue(expiry.getEpochSecond()).build())
        .putFields("jti", text(UUID.randomUUID().toString()))
        .build();
    return keys.sign(options.getMacKey(), data(refresh.secret())).compose(mac -> keys.signJwt(
        options.getAccessTokenKey(), ACCESS_TOKEN_TYPE, claims).map(
            jwt -> new TokenIssue(TokenSet.newBuilder()
                .setSession(session.getName()).setAccessToken(jwt.getToken())
                .setAccessTokenExpireTime(Timestamp.newBuilder().setSeconds(expiry.getEpochSecond()))
                .setRefreshToken(refresh.toString()).build(),
                Tables.RefreshToken.newBuilder().setId(refresh.id())
                    .setSession(session.getName()).setMac(mac.getSignature()).build())));
  }

  /**
   * Trades a refresh token whose secret is right: the session must be live, and the token unused. The new tokens are
   * signed first; then one transaction takes the old token, extends the idle lifetime and keeps the new token, so a
   * failure leaves the old token unspent. A token that a refresh took before ends its session.
   */
  private Future<TokenSet> trade(Tables.RefreshToken token) {
    return sessions.findLive(token.getSession()).compose(found -> {
      if (found.isEmpty()) {
        return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has ended."));
      }
      Tables.Session session = found.get();
      return sign(session).compose(issue -> sessions.database().transaction(tx -> refreshTokens.use(token.getId())
          .compose(first -> first
              ? sessions.touch(session.getUid(), options.getIdleLifetime(), TOUCH)
                  .compose(ignored -> refreshTokens.insert(issue.refreshRow())).map(row -> Optional.of(issue.tokens()))
              : Future.succeededFuture(Optional.<TokenSet> empty()))))
          .compose(tokens -> tokens.isPresent() ? Future.succeededFuture(tokens.get())
              : end(session).compose(ignored -> Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED,
                  "The refresh token was used before, so its session has ended."))));
    });
  }

  private static <T> Future<T> invalid() {
    return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The refresh token is not valid."));
  }

  private static byte[] data(String secret) {
    return ("refresh\0" + secret).getBytes(StandardCharsets.UTF_8);
  }

  private static Value text(String value) {
    return Value.newBuilder().setStringValue(value).build();
  }
}
