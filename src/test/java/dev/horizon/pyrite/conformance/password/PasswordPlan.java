package dev.horizon.pyrite.conformance.password;

import dev.horizon.pyrite.conformance.ConformanceModule;
import dev.horizon.pyrite.conformance.Plan;
import java.util.List;
import java.util.function.Supplier;

/**
 * The plan of passwords: how they sign in, refuse guesses, and are enrolled.
 */
public final class PasswordPlan implements Plan {

  @Override
  public String name() {
    return "passwords";
  }

  @Override
  public List<Supplier<ConformanceModule>> modules() {
    return List.<Supplier<ConformanceModule>> of(PasswordSignIn::new, AddressNormalization::new, WrongPassword::new,
        PasswordStorageFailure::new, WrongPasswordRetry::new, PasswordGuessing::new, UnknownAddress::new,
        PasswordOfOtherAccount::new, PasswordLength::new, PasswordReplacement::new, WrongAnswersAtOnce::new);
  }
}
