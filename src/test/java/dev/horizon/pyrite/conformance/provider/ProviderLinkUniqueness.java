package dev.horizon.pyrite.conformance.provider;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccount;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A provider account that one account links is not linked to another.")
final class ProviderLinkUniqueness extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccount("bob", "bob@acme.dev", PASSWORD));
    call(new StartExchange(ANNE, "link-provider", ""));
    call(new SignInAtProvider("oauth", "shared"));
    call(new StartExchange(BOB, "link-provider", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.ALREADY_EXISTS, new SignInAtProvider("oauth", "shared")),
        Requirement.PROVIDER_LINK_UNIQUE);
  }
}
