package dev.horizon.pyrite.conformance.agreement;

import dev.horizon.pyrite.conformance.CodeDelivery;
import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Variant;

/**
 * A scenario of agreement with OpenFGA. It asks nothing of codes, so it runs in the variant that gets them from
 * responses only, over each transport.
 */
abstract class AgreementModule extends ConformanceModule {

  @Override
  public boolean appliesTo(Variant variant) {
    return variant.delivery() == CodeDelivery.RESPONSE;
  }
}
