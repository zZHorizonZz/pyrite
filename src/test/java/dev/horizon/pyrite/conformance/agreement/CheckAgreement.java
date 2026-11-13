package dev.horizon.pyrite.conformance.agreement;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.BuildAgreementWorld;
import dev.horizon.pyrite.conformance.condition.ExpectCheckAgreement;

@Summary("A check agrees with OpenFGA on every relation.")
final class CheckAgreement extends AgreementModule {

  @Override
  protected void run() {
    call(new BuildAgreementWorld());
    callAndStopOnFailure(new ExpectCheckAgreement(), Requirement.AGREEMENT_CHECK);
  }
}
