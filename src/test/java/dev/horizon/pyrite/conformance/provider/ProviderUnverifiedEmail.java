package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.FakeProvider;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An email that the provider did not verify reaches no account.")
final class ProviderUnverifiedEmail extends ProviderModule {

  @Override
  protected void run() {
    prepareVerifiedAnne();
    call(new StartExchange("", "sign-in-at-provider-by-email", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new SignInAtProvider("oauth", EMAIL,
        FakeProvider.Forgery.UNVERIFIED_EMAIL)), Requirement.PROVIDER_EMAIL_VERIFIED);
  }
}
