package dev.horizon.pyrite.conformance.factor;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.conformance.condition.ExpectDifferentSecrets;
import dev.horizon.pyrite.conformance.condition.KeepTotpSecret;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("Each enrolment gets its own secret.")
final class TotpSecretPerEnrolment extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "enrol-totp", ""));
    call(new KeepTotpSecret(Value.OTHER_TOTP_SECRET));
    call(new StartExchange(ANNE, "enrol-totp", ""));
    call(new KeepTotpSecret(Value.TOTP_SECRET));
    callAndStopOnFailure(new ExpectDifferentSecrets(), Requirement.TOTP_PROVISIONING);
  }
}
