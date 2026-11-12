package dev.horizon.pyrite.conformance.condition;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.pyrite.authentication.v1.Identifier;
import dev.horizon.pyrite.conformance.Condition;
import dev.horizon.pyrite.conformance.Environment;
import dev.horizon.pyrite.conformance.Value;

/**
 * Checks that the last identifier added is of a kind, a value and an account, and unverified.
 */
public record ExpectIdentifier(String kind, String value, String account) implements Condition {

  @Override
  public void evaluate(Environment environment) {
    Identifier identifier = environment.get(Value.IDENTIFIER);
    assertThat(identifier.getKind()).as("kind").isEqualTo(kind);
    assertThat(identifier.getValue()).as("value").isEqualTo(value);
    assertThat(identifier.getAccount()).as("account").isEqualTo(account);
    assertThat(identifier.getVerified()).as("verified").isFalse();
  }
}
