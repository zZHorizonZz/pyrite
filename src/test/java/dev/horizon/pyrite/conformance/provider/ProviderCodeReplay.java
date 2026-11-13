package dev.horizon.pyrite.conformance.provider;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerSpentProviderCode;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.PrepareAccounts;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A code that an exchange traded is wrong in another.")
final class ProviderCodeReplay extends AccountModule {

  @Override
  protected void run() {
    call(new PrepareAccounts());
    call(new StartExchange("", "sign-up-at-provider", ""));
    call(new SignInAtProvider("oidc", "alice"));
    call(new StartExchange("", "sign-in-at-provider", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new AnswerSpentProviderCode("oidc")),
        Requirement.PROVIDER_CALLBACK);
  }
}
