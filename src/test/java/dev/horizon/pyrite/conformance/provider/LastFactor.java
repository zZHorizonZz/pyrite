package dev.horizon.pyrite.conformance.provider;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.authentication.v1.Factor;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.DeleteFactor;
import dev.horizon.pyrite.conformance.condition.ExpectStatus;
import dev.horizon.pyrite.conformance.condition.SignInAtProvider;
import dev.horizon.pyrite.conformance.condition.StartExchange;
import io.vertx.grpc.common.GrpcStatus;

@Summary("The last factor that signs an account in on its own stays; with another, it goes.")
final class LastFactor extends ProviderModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new DeleteFactor("accounts/anne", ANNE,
        Factor.Kind.PASSWORD)), Requirement.FACTOR_LAST);
    call(new StartExchange(ANNE, "link-provider", ""));
    call(new SignInAtProvider("oauth", "anne-at-provider"));
    callAndStopOnFailure(new DeleteFactor("accounts/anne", ANNE, Factor.Kind.PASSWORD), Requirement.FACTOR_LAST);
    callAndStopOnFailure(new ExpectStatus(GrpcStatus.FAILED_PRECONDITION, new DeleteFactor("accounts/anne", ANNE,
        Factor.Kind.PROVIDER)), Requirement.FACTOR_LAST);
  }
}
