package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.FakeProvider;
import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.PrepareAccounts;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A callback with the state of another exchange is wrong.")
final class ProviderState extends AccountModule {

  @Override
  protected void run() {
    call(new PrepareAccounts());
    call(new StartExchange("", "sign-up-at-provider", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new SignInAtProvider("oidc", "alice",
        FakeProvider.Forgery.STATE)), Requirement.PROVIDER_CALLBACK);
  }
}
