package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last flow is done with a result that holds no session and nothing issued.
 */
public record ExpectEmptyResult() implements Condition {

  @Override
  public void evaluate(Environment environment) {
    Flow flow = environment.get(Value.FLOW);
    assertThat(flow.hasResult()).as("done").isTrue();
    assertThat(flow.getResult().getSessionsCount()).as("sessions").isZero();
    assertThat(flow.getResult().getIssuedCount()).as("issued").isZero();
  }
}
