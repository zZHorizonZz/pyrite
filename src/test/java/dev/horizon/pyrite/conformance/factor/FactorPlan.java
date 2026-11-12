package dev.horizon.pyrite.conformance.factor;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of second factors: TOTP authenticators and recovery codes.
 */
public final class FactorPlan implements Plan {

  @Override
  public String name() {
    return "factors";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(TotpProvisioningUri::new, TotpSecretPerEnrolment::new, TotpActivation::new,
        TotpSignIn::new, TotpWindow::new, TotpReplay::new, TotpCodeSyntax::new, TotpOfOtherSecret::new, SameKindFactor::new,
        RecoveryCodeIssue::new, RecoveryCodeOnce::new, RecoveryCodeReplacement::new, RecoveryCodeForgery::new,
        RecoveryCodeOfOtherAccount::new);
  }
}
