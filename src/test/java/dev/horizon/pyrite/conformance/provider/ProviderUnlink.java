package dev.horizon.pyrite.conformance.provider;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.CreateAccount;
import dev.horizon.pyrite.conformance.condition.DeleteFactor;
import dev.horizon.pyrite.conformance.condition.ExpectFactorResult;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("A removed provider account signs nobody in, and another account can link it.")
final class ProviderUnlink extends ProviderModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new CreateAccount("bob", "bob@acme.dev", PASSWORD));
    call(new StartExchange(ANNE, "link-provider", ""));
    call(new SignInAtProvider("oauth", "shared"));
    callAndStopOnFailure(new DeleteFactor("accounts/anne", ANNE, Factor.Kind.PROVIDER), Requirement.FACTOR_DELETE);
    call(new StartExchange("", "sign-in-at-provider", ""));
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new SignInAtProvider("oauth", "shared")),
        Requirement.FACTOR_DELETE);
    call(new StartExchange(BOB, "link-provider", ""));
    callAndStopOnFailure(new SignInAtProvider("oauth", "shared"), Requirement.FACTOR_DELETE);
    callAndStopOnFailure(new ExpectFactorResult(Factor.Kind.PROVIDER, "accounts/bob"), Requirement.FACTOR_DELETE);
  }
}
