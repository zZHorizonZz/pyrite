package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectProviderAccount;
import dev.horizon.pyrite.conformance.condition.PrepareAccounts;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("An exchange that creates accounts creates one for an unlinked provider account, which later sign-ins reach.")
final class ProviderSignUp extends AccountModule {

  @Override
  protected void run() {
    call(new PrepareAccounts());
    call(new StartExchange("", "sign-up-at-provider", ""));
    callAndStopOnFailure(new SignInAtProvider("oidc", "alice"), Requirement.PROVIDER_SIGN_UP);
    callAndStopOnFailure(new ExpectProviderAccount(""), Requirement.PROVIDER_SIGN_UP);
    call(new StartExchange("", "sign-in-at-provider", ""));
    callAndStopOnFailure(new SignInAtProvider("oidc", "alice"), Requirement.PROVIDER_SIGN_UP);
    callAndStopOnFailure(new ExpectProviderAccount("*"), Requirement.PROVIDER_SIGN_UP);
  }
}
