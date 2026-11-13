package dev.horizon.pyrite.conformance.code;

import static dev.horizon.pyrite.PyriteFixture.ANNE;

import dev.horizon.pyrite.conformance.AccountModule;
import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.AnswerCode;
import dev.horizon.pyrite.conformance.condition.ExpectAddressVerification;
import dev.horizon.pyrite.conformance.condition.ExpectDelivery;
import dev.horizon.pyrite.conformance.condition.StartExchange;

@Summary("The code sent to an address verifies it.")
final class CodeVerification extends AccountModule {

  @Override
  protected void run() {
    prepareAnne();
    call(new StartExchange(ANNE, "verify", EMAIL));
    callAndStopOnFailure(new ExpectDelivery(EMAIL, "email"), Requirement.CODE_DELIVERY);
    callAndStopOnFailure(new AnswerCode(), Requirement.CODE_PROOF);
    callAndStopOnFailure(new ExpectAddressVerification(), Requirement.CODE_PROOF);
  }
}
