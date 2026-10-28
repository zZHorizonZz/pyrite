package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.PyriteException;
import dev.horizon.pyrite.authentication.v1.Principal;
import dev.horizon.pyrite.schema.authentication.v1.Tables;
import io.vertx.core.Future;
import io.vertx.grpc.common.GrpcStatus;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

final class SessionImpl implements Session {

  private static final Duration TOUCH = Duration.ofMinutes(1);

  private final AccountTypeImpl type;
  private final Tables.Session row;
  private final Optional<SessionToken> token;

  SessionImpl(AccountTypeImpl type, Tables.Session row, Optional<SessionToken> token) {
    this.type = type;
    this.row = row;
    this.token = token;
  }

  static byte[] data(String secret) {
    return ("session\0" + secret).getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String name() {
    return row.getName();
  }

  @Override
  public Optional<String> token() {
    return token.map(SessionToken::toString);
  }

  @Override
  public Instant expireTime() {
    return Instant.parse(row.getExpireTime());
  }

  @Override
  public boolean isLive() {
    Instant now = type.schema().clock().instant();
    return !row.hasDeleteTime() && now.isBefore(expireTime()) && now.isBefore(Instant.parse(row.getIdleExpireTime()));
  }

  @Override
  public Future<Principal> authenticate(String secret) {
    if (!isLive()) {
      return Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session has ended."));
    }
    return type.macKey().compose(key -> key.verify(data(secret), row.getTokenMac().toByteArray(), ""))
        .compose(verdict -> verdict.hasVerified() ? touch()
            : Future.failedFuture(new PyriteException(GrpcStatus.UNAUTHENTICATED, "The session token is not"
                + " valid.")))
        .compose(touched -> type.account(row.getParent())
            .recover(failure -> Future.failedFuture(failure instanceof PyriteException refusal
                && refusal.status() == GrpcStatus.NOT_FOUND
                    ? new PyriteException(GrpcStatus.UNAUTHENTICATED, "The account of the session is deleted.")
                    : failure))
            .map(account -> Principal.newBuilder().setSubject(account.subject()).setAccount(account.name())
                .setSession(name()).setExpireTime(timestamp(touched.expireTime())).build()));
  }

  @Override
  public Future<Void> end() {
    String now = type.schema().clock().instant().toString();
    return save(row.toBuilder().setDeleteTime(now).setPurgeTime(now)).mapEmpty();
  }

  @Override
  public dev.horizon.pyrite.authentication.v1.Session message() {
    return dev.horizon.pyrite.authentication.v1.Session.newBuilder().setName(name()).setAccount(row.getParent())
        .setExpireTime(timestamp(expireTime())).build();
  }

  private Future<Session> touch() {
    Instant now = type.schema().clock().instant();
    if (Instant.parse(row.getUpdateTime()).plus(TOUCH).isAfter(now)) {
      return Future.succeededFuture(this);
    }
    Instant idle = now.plus(type.options().idleLifetime());
    return save(row.toBuilder().setIdleExpireTime((idle.isBefore(expireTime()) ? idle : expireTime()).toString()))
        .recover(conflict -> Future.succeededFuture(this));
  }

  private Future<Session> save(Tables.Session.Builder changed) {
    return type.sessionRows().update(changed.setRevision(row.getRevision() + 1)
        .setUpdateTime(type.schema().clock().instant().toString()).build(), row.getUid(), row.getRevision(), name())
        .map(saved -> new SessionImpl(type, saved, token));
  }

  private static Timestamp timestamp(Instant time) {
    return Timestamp.newBuilder().setSeconds(time.getEpochSecond()).setNanos(time.getNano()).build();
  }
}
