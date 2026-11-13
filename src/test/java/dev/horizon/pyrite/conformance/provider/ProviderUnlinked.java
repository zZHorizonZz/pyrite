package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.PrepareAccounts;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("An exchange that does not create accounts refuses an unlinked provider account.")
final class ProviderUnlinked extends AccountModule {

  @Override
  protected void run() {
    call(new PrepareAccounts());
    call(new StartExchange("", "sign-in-at-provider", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new SignInAtProvider("oidc", "nobody")),
        Requirement.PROVIDER_LINK_ONLY);
  }
}
