package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * Signing in at providers: sign-up, link-only exchanges, links, and the callbacks that are wrong.
 */
public final class ProviderPlan implements Plan {

  @Override
  public String name() {
    return "providers";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(ProviderSignUp::new, ProviderUnlinked::new, ProviderLink::new,
        ProviderLinkUniqueness::new, ProviderState::new, ProviderNonce::new, ProviderCodeChallenge::new,
        ProviderCodeReplay::new, ProviderEmailSignIn::new, ProviderEmailConfirm::new, ProviderUnverifiedEmail::new,
        ProviderEmailOfUnverifiedIdentifier::new, ProviderUnlink::new, FactorDeletePermission::new,
        LastFactor::new);
  }
}
