package dev.horizon.pyrite.conformance.provider;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectFactorResult;
import dev.horizon.pyrite.conformance.condition.ExpectProviderAccount;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("A signed-in account links a provider account without ID tokens, whose sign-in then reaches it.")
final class ProviderLink extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "link-provider", ""));
    callAndStopOnFailure(new SignInAtProvider("oauth", "anne-at-provider"), Requirement.PROVIDER_LINK);
    callAndStopOnFailure(new ExpectFactorResult(Factor.Kind.PROVIDER, "accounts/anne"), Requirement.PROVIDER_LINK);
    call(new StartExchange("", "sign-in-at-provider", ""));
    callAndStopOnFailure(new SignInAtProvider("oauth", "anne-at-provider"), Requirement.PROVIDER_LINK);
    callAndStopOnFailure(new ExpectProviderAccount("accounts/anne"), Requirement.PROVIDER_LINK);
  }
}
