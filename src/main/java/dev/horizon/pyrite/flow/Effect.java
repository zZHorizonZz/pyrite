package dev.horizon.pyrite.flow;

import dev.horizon.pyrite.authentication.v1.Result;
import dev.horizon.pyrite.identity.Account;
import dev.horizon.pyrite.schema.v1.FlowDefinition;
import io.vertx.core.Future;

/**
 * What a flow does when it ends, for one completion of its declaration, such as {@code ISSUE_SESSION}.
 */
sealed interface Effect permits SessionIssue, IdentifierVerification, CredentialActivation, SessionEnd {

  static Effect of(FlowDefinition.Completion completion) {
    return switch (completion) {
      case ISSUE_SESSION -> new SessionIssue();
      case MARK_VERIFIED -> new IdentifierVerification();
      case ACTIVATE_FACTOR -> new CredentialActivation();
      case END_SESSIONS -> new SessionEnd();
      case COMPLETION_UNSPECIFIED, UNRECOGNIZED -> throw new IllegalStateException("A completion has no kind, which"
          + " the declaration was checked for.");
    };
  }

  Future<Void> apply(Flow flow, Account account, Result.Builder result);
}
