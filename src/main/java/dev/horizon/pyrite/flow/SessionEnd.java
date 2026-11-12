package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import io.vertx.core.Future;

/**
 * Ends the live sessions of the account of a flow, such as after its password changed.
 */
final class SessionEnd implements Effect {

  @Override
  public Future<Void> apply(Flow flow, Account account, Result.Builder result) {
    return account.endSessions();
  }
}
