package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.ExpectTotpProvisioning;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("The provisioning URI names the issuer, SHA-1, six digits and thirty seconds.")
final class TotpProvisioningUri extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "enrol-totp", ""));
    callAndStopOnFailure(new ExpectTotpProvisioning("Acme"), Requirement.TOTP_PROVISIONING);
  }
}
