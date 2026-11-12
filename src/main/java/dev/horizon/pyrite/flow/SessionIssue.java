package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import io.vertx.core.Future;

/**
 * Issues a session of the account of a flow, and gives out its token once.
 */
final class SessionIssue implements Effect {

  @Override
  public Future<Void> apply(Flow flow, Account account, Result.Builder result) {
    return account.issueSession().map(session -> {
      result.addSessions(session.session().message()).addIssued(session.toAny());
      return null;
    });
  }
}
