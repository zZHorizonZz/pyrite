package dev.horizon.pyrite.conformance.agreement;

import dev.horizon.pyrite.conformance.Requirement;
import dev.horizon.pyrite.conformance.Summary;
import dev.horizon.pyrite.conformance.condition.BuildAgreementWorld;
import dev.horizon.pyrite.conformance.condition.ExpectSubjectAgreement;

@Summary("The subjects of a permission are exactly OpenFGA's users.")
final class SubjectAgreement extends AgreementModule {

  @Override
  protected void run() {
    call(new BuildAgreementWorld());
    callAndStopOnFailure(new ExpectSubjectAgreement(), Requirement.AGREEMENT_SUBJECTS);
  }
}
