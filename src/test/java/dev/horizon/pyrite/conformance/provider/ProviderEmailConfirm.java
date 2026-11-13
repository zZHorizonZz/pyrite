package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerPassword;
import dev.horizon.pyrite.conformance.condition.ExpectChallenge;
import dev.horizon.pyrite.conformance.condition.ExpectPendingLink;
import dev.horizon.pyrite.conformance.condition.ExpectProviderAccount;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("A provider account of a verified email asks for a factor of its account, then is linked.")
final class ProviderEmailConfirm extends ProviderModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new StartExchange("", "confirm-at-provider-by-email", ""));
    callAndStopOnFailure(new SignInAtProvider("oidc", EMAIL), Requirement.PROVIDER_EMAIL_CONFIRM);
    callAndStopOnFailure(new ExpectPendingLink(EMAIL), Requirement.PROVIDER_EMAIL_CONFIRM);
    callAndStopOnFailure(new ExpectChallenge("pyrite.authentication.v1.PasswordAnswer", true),
        Requirement.PROVIDER_EMAIL_CONFIRM);
    callAndStopOnFailure(new AnswerPassword(PASSWORD), Requirement.PROVIDER_EMAIL_CONFIRM);
    callAndStopOnFailure(new ExpectProviderAccount("accounts/anne"), Requirement.PROVIDER_EMAIL_CONFIRM);
    call(new StartExchange("", "sign-in-at-provider", ""));
    callAndStopOnFailure(new SignInAtProvider("oidc", EMAIL), Requirement.PROVIDER_EMAIL_CONFIRM);
    callAndStopOnFailure(new ExpectProviderAccount("accounts/anne"), Requirement.PROVIDER_EMAIL_CONFIRM);
  }
}
