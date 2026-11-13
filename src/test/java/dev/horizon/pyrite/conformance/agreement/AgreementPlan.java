package dev.horizon.pyrite.conformance.agreement;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of agreement: Pyrite answers as OpenFGA does, on a world of resources and grants that both hold.
 */
public final class AgreementPlan implements Plan {

  @Override
  public String name() {
    return "agreement";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(CheckAgreement::new, ObjectAgreement::new, SubjectAgreement::new);
  }
}
