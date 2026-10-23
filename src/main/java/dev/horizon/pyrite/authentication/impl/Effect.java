package dev.horizon.pyrite.authentication.impl;

import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import io.vertx.core.Future;

sealed interface Effect permits IssueSession, MarkVerified, ActivateFactor, EndSessions {

  static Effect of(FlowDefinition.Completion completion) {
    return switch (completion) {
      case ISSUE_SESSION -> new IssueSession();
      case MARK_VERIFIED -> new MarkVerified();
      case ACTIVATE_FACTOR -> new ActivateFactor();
      case END_SESSIONS -> new EndSessions();
      case COMPLETION_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("A completion has no kind, which"
          + " the declaration was checked for.");
    };
  }

  Future<Void> apply(FlowImpl flow, AccountImpl account, Result.Builder result);
}
