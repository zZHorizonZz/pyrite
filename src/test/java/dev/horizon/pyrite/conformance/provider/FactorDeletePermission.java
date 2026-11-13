package dev.horizon.pyrite.conformance.provider;

import static dev.horizon.pyrite.PyriteFixture.ANNE;
import static dev.horizon.pyrite.PyriteFixture.BOB;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.DeleteFactor;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("Only its account removes a factor.")
final class FactorDeletePermission extends ProviderModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "link-provider", ""));
    call(new SignInAtProvider("oauth", "anne-at-provider"));
    callAndStopOnFailure(
        new ExpectStatus(GrpcStatus.PERMISSION_DENIED, new DeleteFactor("accounts/anne", BOB, Factor.Kind.PROVIDER)),
        Requirement.FACTOR_DELETE);
  }
}
