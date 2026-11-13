package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Challenge;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;
import java.util.List;

/**
 * Checks that the last exchange takes an answer of a message now, or that it does not.
 *
 * @param method the full name of the message, such as {@code pyrite.authentication.v1.PasswordAnswer}
 */
public record ExpectChallenge(String method, boolean taken) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    List<String> methods = environment.get(Value.EXCHANGE).getChallengesList().stream().map(Challenge::getMethod)
        .toList();
    if (taken) {
      assertThat(methods).contains(method);
    } else {
      assertThat(methods).doesNotContain(method);
    }
  }
}
