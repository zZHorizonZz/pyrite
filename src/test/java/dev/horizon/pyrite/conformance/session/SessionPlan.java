package dev.horizon.pyrite.conformance.session;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of sessions: how they are issued, authenticated, read, ended, and purged.
 */
public final class SessionPlan implements Plan {

  @Override
  public String name() {
    return "sessions";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(SessionPrincipal::new, TokenPerSignIn::new,
        AuthenticationWithoutCredential::new, SecretForgery::new,
        SecretBorrowing::new, TokenSyntax::new, SessionIdleLifetime::new, SessionIdleExtension::new, SessionLifetime::new,
        SessionEnd::new, AccountDeletion::new, AccountStorageFailure::new, SessionRead::new, ForeignSessionEnd::new,
        SessionPurge::new);
  }
}
