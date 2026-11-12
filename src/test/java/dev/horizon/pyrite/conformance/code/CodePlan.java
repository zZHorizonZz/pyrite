package dev.horizon.pyrite.conformance.code;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of codes: how they prove an address, and recover an account.
 */
public final class CodePlan implements Plan {

  @Override
  public String name() {
    return "codes";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(CodeVerification::new, WrongCode::new, CodeExpiry::new, CodeOfOtherFlow::new,
        AddressOfOtherAccount::new, RecoveryWithoutVerification::new, Recovery::new, RecoveryCodeFirst::new,
        RecoveryWrongCode::new, UnknownRecoveryAddress::new, CodeGuessing::new);
  }
}
