package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectProviderAccount;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("A provider account of a verified email signs in to its account, which later sign-ins reach.")
final class ProviderEmailSignIn extends ProviderModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new StartExchange("", "sign-in-at-provider-by-email", ""));
    callAndStopOnFailure(new SignInAtProvider("oidc", EMAIL), Requirement.PROVIDER_EMAIL_SIGN_IN);
    callAndStopOnFailure(new ExpectProviderAccount("accounts/anne"), Requirement.PROVIDER_EMAIL_SIGN_IN);
    call(new StartExchange("", "sign-in-at-provider", ""));
    callAndStopOnFailure(new SignInAtProvider("oidc", EMAIL), Requirement.PROVIDER_EMAIL_SIGN_IN);
    callAndStopOnFailure(new ExpectProviderAccount("accounts/anne"), Requirement.PROVIDER_EMAIL_SIGN_IN);
  }
}
