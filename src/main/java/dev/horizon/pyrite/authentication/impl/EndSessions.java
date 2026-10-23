package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Result;
import io.vertx.core.Future;

final class EndSessions implements Effect {

  @Override
  public Future<Void> apply(FlowImpl flow, AccountImpl account, Result.Builder result) {
    return account.deleteSessions();
  }
}
