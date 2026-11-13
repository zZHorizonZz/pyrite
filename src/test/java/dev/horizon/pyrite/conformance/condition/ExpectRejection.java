package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import dev.horizon.pyrite.crypto.v1.Rejected;
import dev.horizon.pyrite.crypto.v1.VerifyResponse;
import java.util.Set;

/**
 * Checks that the last verification rejected, for a reason, or for one of a few.
 */
public record ExpectRejection(Set<Rejected.Reason> reasons) implements Condition {

  public ExpectRejection(Rejected.Reason reason) {
    this(Set.of(reason));
  }

  @Override
  public void evaluate(Environment environment) {
    VerifyResponse verdict = environment.get(Value.VERIFICATION);
    assertThat(verdict.hasVerified()).as("verified").isFalse();
    assertThat(reasons).as("the reason").contains(verdict.getRejected().getReason());
  }
}
