package dev.horizon.pyrite.session;

import static dev.horizon.basepoint.expression.Expression.add;
import static dev.horizon.basepoint.expression.Expression.now;

import com.google.protobuf.Struct;
import com.google.protobuf.Timestamp;
import com.google.protobuf.Value;
import dev.horizon.cinder.Database;
import dev.horizon.pyrite.AuthenticationOptions;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.TokenSet;
import dev.horizon.pyrite.crypto.v1.Expected;
import dev.horizon.pyrite.key.KeyType;
import dev.horizon.pyrite.key.SigningKey;
import dev.horizon.pyrite.resource.Subject;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import dev.horizon.pyrite.storage.RefreshTokenBackend;
import dev.horizon.pyrite.storage.SessionBackend;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The sessions of accounts: it issues them and their tokens, authenticates access tokens, trades refresh tokens for
 * new ones, and ends sessions.
 *
 * <p>
 * An access token is a JWT of type {@code at+jwt}, which the access token key of the options signs, with the claims
 * {@code iss}, {@code sub}, {@code sid}, {@code iat}, {@code exp} and {@code jti}. A service verifies it with the key's
 * public key, without a call to Pyrite, and takes it until it expires, even after its session ends; {@code Authenticate}
 * also checks that the session has not ended.
 *
 * <p>
 * A refresh token is opaque, and works once: a refresh trades it for the next, and extends the idle lifetime of the
 * session. A token that comes back after its trade was copied, so its session ends. Its row keeps only the MAC of its
 * secret, which the MAC key of the options signs. The database's clock decides when a session expires.
 */
public final class SessionType {

  /**
   * The first segment of the name of a session, such as {@code sessions/3d5e7a1c-0f42-4b8e-9c61-2a7d9e4f1b03}.
   */
  public static final String SESSIONS = "sessions/";

  /**
   * The {@code typ} of an access token.
   */
  public static final String ACCESS_TOKEN_TYPE = "at+jwt";

  /**
   * How often a refresh extends the idle lifetime of a session, at most.
   */
  private static final Duration TOUCH = Duration.ofMinutes(1);

  private final SessionBackend sessions;
  private final RefreshTokenBackend refreshTokens;
  private final AuthenticationOptions options;
  private final KeyType keys;
  private final SessionObserver observer;

  public SessionType(Database database, AuthenticationOptions options, KeyType keys, SessionObserver observer) {
    this.sessions = new SessionBackend(database);
    this.refreshTokens = new RefreshTokenBackend(database);
    this.options = options;
    this.keys = keys;
    this.observer = observer;
  }

  /**
   * Issues a session of an account, which expires after the session lifetime of the options and idles out after
   * their idle lifetime, and tells the handler of issued sessions. The session has no tokens yet;
   * {@link Session#issueTokens()} gives them.
   *
   * @param account the name of the account
   * @return the session
   */
  public Future<Session> issue(String account) {
    return sessions.insert(Tables.Session.newBuilder().setName(SESSIONS + UUID.randomUUID()).setParent(account)
        .build(),
        Map.of("expire_time", add(now(), options.getSessionLifetime()), "idle_expire_time", add(now(),
            options.getIdleLifetime())))
        .map(row -> new Session(this, row))
        .onSuccess(session -> observer.issue(session.message()));
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
   * Authenticates an access token: its signature, its type, its issuer and its expiry, and that its session has not
   * ended.
   *
   * @param token the token, as the client holds it
   * @return the live session of the token, or a failure with {@code UNAUTHENTICATED}: {@code The access token is not
   * valid: ...} with the reason, or {@code The session has ended.}
   */
  public Future<Session> authenticate(String token) {
    return accessTokenKey().compose(key -> key.verify(token, Expected.newBuilder().setType(ACCESS_TOKEN_TYPE)
        .setIssuer(options.getIssuer()).build())).compose(verdict -> {
          if (verdict.hasRejected()) {
            return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The access token is not"
                + " valid: " + verdict.getRejected().getMessage()));
          }
          String session = verdict.getVerified().getClaims().getFieldsOrDefault("sid", Value.getDefaultInstance())
              .getStringValue();
          return sessions.findLive(session).compose(found -> found.isPresent()
              ? Future.succeededFuture(new Session(this, found.get()))
              : Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has ended.")));
        });
  }

  /**
   * Trades a refresh token for new tokens of its session, and extends the idle lifetime of the session, at most once
   * a minute. A token that a refresh traded before ends its session.
   *
   * @param token the refresh token, as the client holds it
   * @return the new tokens, or a failure with {@code UNAUTHENTICATED}: {@code The refresh token is not valid.} when it
   * is malformed or its secret is not the token's, {@code The session has ended.}, or {@code The refresh token was
   * used before, so its session has ended.}
   */
  public Future<TokenSet> refresh(String token) {
    Token parsed;
    try {
      parsed = Token.parse(token);
    } catch (PyriteException malformed) {
      return Future.failedFuture(malformed);
    }
    return refreshTokens.findById(parsed.id()).compose(found -> found.isEmpty() ? invalid()
        : macKey().compose(key -> key.verify(data(parsed.secret()), found.get().getMac().toByteArray(), ""))
            .compose(verdict -> verdict.hasVerified() ? trade(found.get()) : invalid()));
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
   * Deletes the rows of sessions that ended, or that passed their lifetime, with their refresh tokens. A session that
   * idled out stays until the end of its lifetime.
   *
   * @return the number of rows of sessions
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

  /**
   * Issues a new access token and a new refresh token of a session.
   */
  Future<TokenSet> issueTokens(Session session) {
    Token refresh = Token.generate();
    Instant issued = Instant.now();
    Instant expiry = issued.plus(options.getAccessTokenLifetime());
    Struct claims = Struct.newBuilder()
        .putFields("iss", text(options.getIssuer()))
        .putFields("sub", text(Subject.account(session.account()).toString()))
        .putFields("sid", text(session.name()))
        .putFields("iat", Value.newBuilder().setNumberValue(issued.getEpochSecond()).build())
        .putFields("exp", Value.newBuilder().setNumberValue(expiry.getEpochSecond()).build())
        .putFields("jti", text(UUID.randomUUID().toString()))
        .build();
    return macKey().compose(key -> key.sign(data(refresh.secret())))
        .compose(mac -> refreshTokens.insert(Tables.RefreshToken.newBuilder().setId(refresh.id())
            .setSession(session.name()).setMac(mac.getSignature()).build()))
        .compose(row -> accessTokenKey().compose(key -> key.jwt(ACCESS_TOKEN_TYPE, claims)))
        .map(signed -> TokenSet.newBuilder().setSession(session.name()).setAccessToken(signed.getToken())
            .setAccessTokenExpireTime(Timestamp.newBuilder().setSeconds(expiry.getEpochSecond()))
            .setRefreshToken(refresh.toString()).build());
  }

  /**
   * Trades a refresh token whose secret is right: the session must be live, and the token unused.
   */
  private Future<TokenSet> trade(Tables.RefreshToken token) {
    return sessions.findLive(token.getSession()).compose(found -> {
      if (found.isEmpty()) {
        return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has ended."));
      }
      Session session = new Session(this, found.get());
      return refreshTokens.use(token.getId()).compose(first -> first
          ? sessions.touch(found.get().getUid(), options.getIdleLifetime(), TOUCH).compose(ignored -> issueTokens(
              session))
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

  private Future<SigningKey> macKey() {
    return keys.signing(options.getMacKey());
  }

  private Future<SigningKey> accessTokenKey() {
    return keys.signing(options.getAccessTokenKey());
  }
}
