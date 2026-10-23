package dev.horizon.pyrite.authentication.impl;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.authentication.v1.SessionToken;
import io.vertx.core.Future;

final class IssueSession implements Effect {

  @Override
  public Future<Void> apply(FlowImpl flow, AccountImpl account, Result.Builder result) {
    return account.createSession().map(session -> {
      result.addResources(session.toMessage()).addIssued(Any.pack(SessionToken.newBuilder()
          .setToken(session.token().orElseThrow()).setSession(session.name())
          .setExpireTime(Timestamp.newBuilder().setSeconds(session.expireTime().getEpochSecond())
              .setNanos(session.expireTime().getNano()))
          .build()));
      return null;
    });
  }
}
