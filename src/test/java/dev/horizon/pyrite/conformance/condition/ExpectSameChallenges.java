package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.authentication.v1.Exchange;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last exchange takes the same answers as an exchange kept aside.
 */
public record ExpectSameChallenges(Value<Exchange> other) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    assertThat(environment.get(Value.EXCHANGE).getChallengesList()).extracting(Challenge::getMethod).isEqualTo(
        environment.get(other).getChallengesList().stream().map(Challenge::getMethod).toList());
  }
}
