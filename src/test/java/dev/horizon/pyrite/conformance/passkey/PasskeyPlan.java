package dev.horizon.pyrite.conformance.passkey;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of passkeys: how they enrol and sign in, through a browser and an authenticator that the test emulates.
 */
public final class PasskeyPlan implements Plan {

  @Override
  public String name() {
    return "passkeys";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(PasskeyRegistration::new, PasskeyExclusion::new, PasskeySignIn::new,
        PhishingResistance::new, PasskeyChallengeReplay::new, PasskeyOrigin::new);
  }
}
