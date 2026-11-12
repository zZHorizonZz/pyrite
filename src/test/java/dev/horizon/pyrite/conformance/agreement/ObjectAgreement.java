package dev.horizon.pyrite.conformance.agreement;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.BuildAgreementWorld;
import dev.horizon.pyrite.conformance.condition.ExpectObjectAgreement;

@Summary("A list of objects is exactly what OpenFGA allows.")
final class ObjectAgreement extends AgreementModule {

  @Override
  protected void run() {
    call(new BuildAgreementWorld());
    callAndStopOnFailure(new ExpectObjectAgreement(), Requirement.AGREEMENT_LIST);
  }
}
