package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.SignIn;

@Summary("An email address signs in however it is cased or padded.")
final class AddressNormalization extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    callAndStopOnFailure(new SignIn("  Anne@ACME.dev ", PASSWORD), Requirement.PASSWORD_SIGN_IN);
  }
}
