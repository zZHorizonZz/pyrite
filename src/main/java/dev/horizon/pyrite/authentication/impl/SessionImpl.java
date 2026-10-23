package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.impl.resource.BaseResource;
import dev.horizon.pyrite.impl.storage.Row;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

final class SessionImpl extends BaseResource implements Session {

  private static final Duration TOUCH = Duration.ofMinutes(1);

  private final Optional<SessionToken> token;

  SessionImpl(SessionTypeImpl type, Row row, Optional<SessionToken> token) {
    super(type, row);
    this.token = token;
  }

  static byte[] data(String secret) {
    return ("session\0" + secret).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public SessionTypeImpl type() {
    return (SessionTypeImpl) super.type();
  }

  @Override
  public Optional<String> token() {
    return token.map(SessionToken::toString);
  }

  @Override
  public Instant expireTime() {
    return field(SessionType.EXPIRE_TIME);
  }

  @Override
  public boolean isLive() {
    Instant now = type().schema().clock().instant();
    return !isDeleted() && now.isBefore(expireTime()) && now.isBefore(field(SessionType.IDLE_EXPIRE_TIME));
  }

  @Override
  public Future<Principal> authenticate(String secret) {
    if (!isLive()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has ended."));
    }
    return type().accounts().macKey().compose(key -> key.verify(data(secret), field(SessionType.TOKEN_MAC), ""))
        .compose(verdict -> verdict.hasVerified() ? touch()
            : Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session token is not"
                + " valid.")))
        .compose(touched -> touched.parent().map(account -> {
          if (account.isEmpty() || account.get().isDeleted()) {
            throw new PyriteException(GrpcStatus.UNAUTHENTICATED, "The account of the session is deleted.");
          }
          return Principal.newBuilder().setSubject(((Account) account.get()).subject())
              .setAccount(account.get().name()).setSession(touched.name())
              .setExpireTime(Timestamp.newBuilder().setSeconds(touched.expireTime().getEpochSecond())
                  .setNanos(touched.expireTime().getNano()))
              .build();
        }));
  }

  @Override
  public Future<Void> end() {
    Instant now = type().schema().clock().instant();
    return change(Map.of(DELETE_TIME, now, PURGE_TIME, now)).mapEmpty();
  }

  SessionImpl withToken(SessionToken token) {
    return new SessionImpl(type(), row(), Optional.of(token));
  }

  private Future<Session> touch() {
    Instant now = type().schema().clock().instant();
    if (updateTime().plus(TOUCH).isAfter(now)) {
      return Future.succeededFuture(this);
    }
    Instant idle = now.plus(type().accounts().options().idleLifetime());
    return change(Map.of(SessionType.IDLE_EXPIRE_TIME, idle.isBefore(expireTime()) ? idle : expireTime()))
        .map(Session.class::cast).recover(conflict -> Future.succeededFuture(this));
  }
}
