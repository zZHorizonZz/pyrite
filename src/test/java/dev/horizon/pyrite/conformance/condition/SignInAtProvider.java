package dev.horizon.pyrite.conformance.condition;

import dev.horizon.pyrite.FakeProvider;
import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.ProviderAnswer;
import dev.horizon.pyrite.authentication.v1.ProviderRedirect;
import dev.horizon.pyrite.authentication.v1.ProviderRedirects;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Signs in at a provider through the authorization URL of the last exchange, as a browser would, and answers the
 * exchange with what the provider sent to the callback, which it leaves.
 */
public record SignInAtProvider(String provider, String subject, FakeProvider.Forgery forgery) implements Condition {

  /**
   * Signs in at a provider as an account there, changing nothing.
   */
  public SignInAtProvider(String provider, String subject) {
    this(provider, subject, FakeProvider.Forgery.NONE);
  }

  /**
   * Gives the authorization URL of a provider in the challenges of the last exchange.
   */
  public static String authorizationUrl(Environment environment, String provider) throws Exception {
    for (Challenge challenge : environment.get(Value.EXCHANGE).getChallengesList()) {
      if (challenge.getMethod().equals(ProviderAnswer.getDescriptor().getFullName())) {
        for (ProviderRedirect redirect : challenge.getParameters().unpack(ProviderRedirects.class).getRedirectsList()) {
          if (redirect.getProvider().equals(provider)) {
            return redirect.getAuthorizationUrl();
          }
        }
      }
    }
    throw new AssertionError("The last exchange has no challenge of the provider " + provider);
  }

  @Override
  public void evaluate(Environment environment) throws Exception {
    FakeProvider.Callback callback = environment.pyrite().provider.signIn(authorizationUrl(environment, provider),
        subject, forgery);
    environment.put(Value.PROVIDER_CALLBACK, callback);
    environment.answer(ProviderAnswer.newBuilder().setProvider(provider).setCode(callback.code())
        .setState(callback.state()).build());
  }
}
