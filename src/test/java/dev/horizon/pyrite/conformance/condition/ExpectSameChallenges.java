package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Flow;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last flow takes the same answers as a flow kept aside.
 */
public record ExpectSameChallenges(Value<Flow> other) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.FLOW).getChallengesList()).extracting(Challenge::getMethod).isEqualTo(
        environment.get(other).getChallengesList().stream().map(Challenge::getMethod).toList());
  }
}
